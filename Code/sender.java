import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.*;

import java.util.concurrent.ConcurrentLinkedQueue;

/*
	To Do:
		wat if sending 11th pkt bef any of 10 pkt ack arrives?
*/

/*
	Buffer class:
		Stores shared buffer between PacketGenerator and ClientXmitter
*/
class Buffer{
	// Buffer internal vars
	public int max_buf_size;
	public ConcurrentLinkedQueue<byte[]> buf;

	int next_seq_no = 0;

	// Stores count of no of pkts acked 
	public int acked_pkts = 0;

	Buffer(){
		buf = new ConcurrentLinkedQueue<byte[]>();
	}

	void push(byte[] buf){
		if(this.buf.size() >= max_buf_size)
			return;

		buf[0] = (byte)(next_seq_no >> 24);
		buf[1] = (byte)(next_seq_no >> 16);
		buf[2] = (byte)(next_seq_no >>  8);
		buf[3] = (byte)(next_seq_no);
		next_seq_no++;

		this.buf.add(buf);
	}

	boolean is_empty(){
		return buf.size() == 0;
	}

	byte[] pop(){
		if(buf.size() == 0)
			return null;
	
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

	PacketGenerator(Buffer b, int pl, int mp, double pgr){
		buf = b;
		pkt_len = pl;
		max_pkts = mp;
		pkt_gen_rate = pgr;
	}

	// Generate Packets and place in buffer
	public void run(){
		time_btwn_pkts = 1000.0 / pkt_gen_rate;

		while(buf.acked_pkts < max_pkts){
			// Generate & Store a random pkt
			byte[] new_pkt = new byte[pkt_len]; 
			new Random().nextBytes(new_pkt);
			buf.push(new_pkt);

			// Wait till time to gen next pkt
			try{
				Thread.sleep((long)time_btwn_pkts);
			}
			catch(InterruptedException e){
				System.out.println("Errored");
				System.exit(1);
			}
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
	volatile static Timer[] timers       = new Timer[window_size]; 
	volatile static long timer_timestamps[] = new long[window_size]; 

	boolean isSender;

	public ClientXmitter(Buffer b, boolean send_or_recv){
		isSender = send_or_recv;
		if(send_or_recv){
			buf = b;
			
			try{
				client = new DatagramSocket();
			}
			catch(SocketException e){
				System.out.println("Socket Open Error");
				System.exit(1);
			}

			InetAddress IPAddress; 
			try{
				IPAddress = InetAddress.getByName(recvr);
				data_packet = new DatagramPacket(new byte[pkt_len], pkt_len, IPAddress, port);
			}
			catch (UnknownHostException e) {
				System.out.println("IPAddress lookup failed");
				System.exit(1);
			}

			ack_packet  = new DatagramPacket(new byte[ack_len], ack_len);

			for(int i=0; i<window_size; i++)
				retry_attempts[i] = 0;			
		}
	}


	// Xmit Packets from buffer
	public void run(){
		/*
			TimerTask Extended Class for handling timeouts
		*/
		class myTimerTask extends TimerTask{
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

					double timeout_time;
					if(no_of_pkts_sent < 10){
						timeout_time = 100.0;
					}
					else{
						timeout_time = 2*avg_rtt;
					}

					retry_attempts[i]++;
					
					DatagramPacket retry_data_packet;
					try{
						InetAddress IPAddress = InetAddress.getByName(recvr);
						retry_data_packet = new DatagramPacket(new byte[pkt_len], pkt_len, IPAddress, port);
						retry_data_packet.setData(sent_buffer[i]);
					}
					catch (UnknownHostException e) {
						System.out.println("UnknownHostException");
						System.exit(1);
					}
					
					timers[i].schedule(new myTimerTask(timeout_pkt), (long)timeout_time);
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

					data_packet.setData(curr);
					try{
						client.send(data_packet);
					}
					catch (IOException e) {
						System.out.println("IOException at send data pkt");
						System.exit(1);
					}

					// Set timeout timer
					timers[seq_next] = new Timer();
					timers[seq_next].schedule(new myTimerTask(seq_next), (long)timeout_time);
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
				try{
					client.receive(ack_packet);
				}
				catch(IOException e){
					System.out.println("IOException at recv ack pkt");
					System.exit(1);
				}

				int ack_seq = (ack_packet.getData()[0] << 24) + (ack_packet.getData()[1] << 16) + (ack_packet.getData()[2] << 8) 
									+ ack_packet.getData()[3];
				
				// Ack all till ack_seq
				for(int i=seq_beg; i != (ack_seq+1)%window_size; i=(i+1)%window_size){
					// cancel timer
					timers[i].cancel();
					timers[i].purge();
					
					// check time
					long timetaken = System.nanoTime() - timer_timestamps[i];
					timetaken /= 1000000;

					// update rtt
					avg_rtt = ((avg_rtt * buf.acked_pkts) + timetaken)/(buf.acked_pkts + 1);
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
	static void errorExit(String s){
		System.out.println("Error: " + s);
		System.out.println("Error: " + s);
	}

	public static void main(String[] args){
		// Instance Parameters
		String recvr = "localhost";
		
		int port = 1080;
		int pkt_len = 1500;
		int pkt_gen_rate = 10;
		int max_pkts = 1024;
		int window_size = 8;
		int max_buf_size = 24;
		boolean debug = false;

		// Process Command Line Args
		int next_arg = 0;
		for(String arg: args){
			if(next_arg == 0){
				if(arg.equals("-d"))
					debug = true;
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
		PacketGenerator p = new PacketGenerator(buf, pkt_len, max_pkts, pkt_gen_rate);
		ClientXmitter sendThread = new ClientXmitter(buf, true);
		ClientXmitter recvThread = new ClientXmitter(null, false);

		ClientXmitter.recvr = recvr;
		ClientXmitter.port  = port;
		ClientXmitter.pkt_len = pkt_len;
		ClientXmitter.max_pkts = max_pkts;
		ClientXmitter.window_size = window_size;
		ClientXmitter.debug = debug;
		ClientXmitter.ack_len = 40;

		// Start Threads
		p.start();
		sendThread.start();
		recvThread.start();
	}
}
