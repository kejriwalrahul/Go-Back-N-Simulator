import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.*;

/*
	To Do:
		Initializa Params
		Send params to classes
		wat if sending 11th pkt bef any of 10 pkt ack arrives?
*/

/*
	Buffer class:
		Stores shared buffer between PacketGenerator and ClientXmitter
*/
class Buffer{
	// Buffer internal vars
	public int max_buf_size;
	public Queue<byte[]> buf = new Queue<byte[]>();

	int next_seq_no = 0;

	// Stores count of no of pkts acked 
	public acked_pkts = 0;

	void push(byte[] buf){
		if(buf.size() >= max_buf_size)
			return;

		buf[0] = next_seq_no >> 24;
		buf[1] = next_seq_no >> 16;
		buf[2] = next_seq_no >>  8;
		buf[3] = next_seq_no;
		next_seq_no++;

		buf.add(buf);
	}

	boolean is_empty(){
		return buf.size() == 0;
	}

	byte[] pop(){
		if(buf.size() == 0)
			return None;
	
		return buf.remove();
	}
}

/*
	Packet Generation Thread:
		Generates pkts at given rate and store them in buffer
*/
class PacketGenerator extends Thread{
	// Buffer object
	volatile Buffer buf;

	// State Vars
	volatile int pkt_len;
	volatile int max_pkts;
	volatile double pkt_gen_rate;

	// Time between 2 successive pkts
	volatile double time_btwn_pkts;

	// Generate Packets and place in buffer
	public void run(){
		time_btwn_pkts = 1000.0 / pkt_gen_rate;

		while(buf.acked_pkts < max_pkts){
			// Generate & Store a random pkt
			byte[] new_pkt = new byte[pkt_len]; 
			new Random().nextBytes(new_pkt);
			buf.push(new_pkt)

			// Wait till time to gen next pkt
			Thread.sleep(time_btwn_pkts);
		}
	}
}

/*
	Client Transmission Thread:
		Xmits pkts to the recvr
*/
class ClientXmitter extends Thread{
	// Buffer object
	volatile static Buffer buf;

	volatile static String recvr;
	volatile static int port, pkt_len, max_pkts;
	volatile static int window_size;
	volatile static boolean debug;

	volatile static DatagramSocket client;
	volatile static DatagramPacket data_packet; 
	volatile static DatagramPacket ack_packet; 

	// Unsure
	volatile static int ack_len;

	// Window params
	volatile static int seq_beg = 0, seq_end = window_size - 2;
	volatile static int seq_next = 0;
	volatile static int seq_left = window_size - 1; 
	volatile static int no_of_pkts_sent = 0;

	volatile static double avg_rtt = 0.0;

	// Stores sent_off pkts
	volatile static byte[][] sent_buffer = new byte[window_size][pkt_len];
	volatile static int[] retry_attempts = new int[window_size];
	volatile static Timer[] timers       = new Timer()[window_size]; 
	volatile static long timer_timestamps = new long[window_size]; 

	boolean isSender;

	public ClientXmitter(Buffer b, boolean send_or_recv){
		isSender = send_or_recv;
		if(send_or_recv){
			buf = b;

			client = new DatagramSocket()
			InetAddress IPAddress = InetAddress.getByName(recvr);

			data_packet = new DatagramPacket(new Byte[pkt_len], pkt_len, IPAddress, port);
			ack_packet  = new DatagramPacket(new Byte[ack_len], ack_len);

			for(int i=0; i<window_size; i++)
				retry_attempts[i] = 0;			
		}
	}


	// Xmit Packets from buffer
	public void run(){
		/*
			TimerTask Extended Class for handling timeouts
		*/
		class myTimerTask extends TimerTask(){
			public int timeout_pkt;

			myTimerTask(int seq_num){
				timeout_pkt = seq_num;
			}

			@Override
			public void run(){

				if(retry_attempts[timeout_pkt] == 5)
					System.exit(1);

				// rexmitt and increment retries
				for(int i=seq_beg; i != timeout_pkt; i = (i+1)%window_size){
					timers[i].cancel();
					timers[i].purge();

					retry_attempts[i]++;
					DatagramPacket retry_data_packet = new DatagramPacket(new Byte[pkt_len], pkt_len, IPAddress, port);
					retry_data_packet.setData(sent_buffer[i]);

					timer[i].schedule(new myTimerTask(timeout_pkt));
					timer_timestamps[i] = System.nanoTime();
				}
			}
		}

		// For Sender Thread
		if(isSender){
			// If pkt count not reached
			while(buf.acked_pkts < max_pkts){
				// If buffer is non-empty, iterate
				if(!buf.is_empty() && seq_left > 0){

					double timeout_time;
					if(no_of_pkts_sent < 10){
						timeout_time = 100.0;
					}
					else{
						timeout_time = 2*avg_rtt;
					}

					byte[] curr = buf.pop();
					sent_buffer[seq_next] = curr;
					retry_attempts[seq_next] = 0;

					data_packet.setData(curr)
					client.send(data_packet);

					// Set timeout timer
					timers[seq_next] = new Timer();
					timers[seq_next].schedule(new myTimerTask(seq_next), timeout_time);
					timer_timestamps[seq_next] = System.nanoTime();

					// update seq_next
					seq_next = (seq_next + 1) % window_size;
					seq_left -= 1;
					no_of_pkts_sent++;
				}
				// Else reiterate
			}
			
			client.close();
		}
		// For reciever thread
		else{
			while(buf.acked_pkts < max_pkts){
				client.receive(ack_packet);

				int ack_seq = (ack_packet[0] << 24) + (ack_packet[1] << 16) + (ack_packet[2] << 8) + ack_packet[3];
				
				// Ack all till ack_seq
				for(int i=seq_beg; i != (ack_seq+1)%window_size; i=(i+1)%window_size){
					// cancel timer
					timers[i].cancel();
					timers[i].purge();
					
					// check time
					long timetaken = System.nanoTime() - timer_timestamps[i];
					timetaken /= 1000000;

					// update rtt
					avg_rtt = ((avg_rtt * acked_pkts) + timetaken)/(acked_pkts + 1);
					buf.acked_pkts ++;

					// update windows vars
					seq_beg = (seq_beg + 1) % window_size;
					seq_end = (seq_end + 1) % window_size;
					seq_left++;
				}
			}
		}
	}
}

/*
	Main Class:
		Parse cmd line args and starts threads
*/
public class sender{
	void errorExit(String s){
		System.out.println("Error: " + s);
		System.out.println("Error: " + s);
	}

	public static void main(String[] args){
		// Instance Parameters
		String recvr;
		int port, pkt_len, pkt_gen_rate, max_pkts;
		int window_size, max_buf_size;
		boolean debug = False;

		// Process Command Line Args
		int next_arg = 0;
		for(String arg: args){
			if(next_arg == 0){
				if(arg.equals("-d"))
					debug = True;
				else if(arg.equals("-s"))
					next_arg = 1;
				else if(arg.equals("-p"))
					next_arg = 2;
				else if(arg.equals("-l"))
					next_arg = 3;
				else if(arg.equals("-r"))
					next_arg = 4;
				else if(arg.equals("-n"))
					next_arg = 5;
				else if(arg.equals("-w"))
					next_arg = 6;
				else if(arg.equals("-b"))
					next_arg = 7;
				else
					errorExit("Incorrect Usage!");
			}
			else{
				switch(next_arg){
					case 1: recvr = arg;
							break;

					case 2: port = Integer.parseInt(arg);
							break;

					case 3: pkt_len = Integer.parseInt(arg);
							break;

					case 4: pkt_gen_rate = Integer.parseInt(arg);
							break;

					case 5: max_pkts = Integer.parseInt(arg);
							break;

					case 6: window_size = Integer.parseInt(arg);
							break;

					case 7: max_buf_size = Integer.parseInt(arg);
							break;

					default: errorExit("Incorrect Usage!");
				}
				next_arg = 0;
			}
		}

		// Create a buffer
		Buffer buf = new Buffer();

		// Create thread objects
		PacketGenerator p = new PacketGenerator(buf);
		ClientXmitter sendThread = new ClientXmitter(buf, True);
		ClientXmitter recvThread = new ClientXmitter(None, False);

		// Start Threads
		c.start();
		p.start();
	}
}
