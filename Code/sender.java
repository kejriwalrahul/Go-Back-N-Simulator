/*
	Program for sending data pkts in Go-Back-N Nimulation

	Written by Rahul Kejriwal
*/

import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.*;

import java.util.concurrent.ConcurrentLinkedQueue;

/*
	Possible Bug:
		1. Wat if sending 11th pkt bef any of 10 pkt ack arrives?
		2. [FIXED] windows size not attained
		3. [FIXED] spurious rexmissions?
*/

/*
	Buffer class:
		Stores shared buffer between PacketGenerator and ClientXmitter
*/
class Buffer{
	// Buffer internal vars
	public int max_buf_size;
	public int window_size;
	public ConcurrentLinkedQueue<byte[]> buf;

	int next_seq_no;

	// Stores count of no of pkts acked 
	public int acked_pkts = 0;

	Buffer(int mbs, int ws){
		max_buf_size = mbs;
		window_size  = ws;

		buf = new ConcurrentLinkedQueue<byte[]>();
		next_seq_no = 0;
	}

	void push(byte[] buf){
		if(this.buf.size() >= max_buf_size)
			return;

		buf[0] = (byte)(next_seq_no >> 24);
		buf[1] = (byte)(next_seq_no >> 16);
		buf[2] = (byte)(next_seq_no >>  8);
		buf[3] = (byte)(next_seq_no);
		this.buf.add(buf);
		
		next_seq_no = (next_seq_no + 1) % window_size;
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
	// volatile cuz acked_pkts keeps changing
	volatile Buffer buf;

	// State Vars
	int pkt_len;
	int max_pkts;
	int pkt_gen_rate;

	// Time between 2 successive pkts
	double time_btwn_pkts;

	PacketGenerator(Buffer b, int pl, int mp, int pgr){
		buf = b;
		pkt_len = pl;
		max_pkts = mp;
		pkt_gen_rate = pgr;
	
		time_btwn_pkts = 1000.0 / pkt_gen_rate;
	}

	// Generate Packets and place in buffer
	public void run(){

		while(buf.acked_pkts < max_pkts){
			// Generate & Store a random pkt
			byte[] new_pkt = new byte[pkt_len]; 
			new Random().nextBytes(new_pkt);
			buf.push(new_pkt);

			// Wait till time to gen next pkt
			try{
				Thread.sleep((long) time_btwn_pkts);
			}
			catch(InterruptedException e){
				System.out.println("Couldn't Sleep till next Packet generation time!");
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
}

/*
	Contains data to be shared across the Xmitter & Recvr Threads
*/
class SharedData{
	// Buffer object
	// volatile cuz acked_pkts keeps changing
	volatile Buffer buf;

	String recvr;
	int port, pkt_len, max_pkts;
	int window_size;
	boolean debug;
	boolean deep_debug;
	int pkt_gen_rate;

	// Assumed
	int ack_len;

	// Shared client socket
	DatagramSocket client;

	// Window params
	int seq_beg, seq_end;
	int seq_next, seq_left;
	int no_of_pkts_sent;

	double avg_rtt = 0.0;

	// Stores sent_off pkts
	byte[][] sent_buffer;
	int[] retry_attempts;
	Timer timer_thread;
	TimerTask[] timer_tasks;
	long[] timer_timestamps; 

	// Stores sender start time
	long startTime;
	long totalRetries;
	long totalXmitPkts;

	public SharedData(Buffer b, String r, int p, int pl, int mp, int ws, boolean d, int al, boolean dd, int pgr){
		// Store instance params
		buf 	= b;
		recvr 	= r;
		port 	= p;
		pkt_len = pl;
		max_pkts= mp;
		window_size = ws;
		debug 	= d;
		ack_len = al;

		deep_debug = dd;
		pkt_gen_rate = pgr;

		// Configure window params
		seq_beg = 0;
		seq_end = window_size - 2;
		seq_next = 0;
		seq_left = window_size - 1;
		no_of_pkts_sent = 0; 

		startTime = System.nanoTime();
		totalRetries = 0;
		totalXmitPkts = 0;

		// Configure arrays for buffer, retries and timers
		sent_buffer 		= new byte[window_size][pkt_len];
		retry_attempts 		= new int[window_size];
		timer_thread 		= new Timer();
		timer_tasks    		= new TimerTask[window_size];
		timer_timestamps 	= new long[window_size];

		// Open Client Socket
		try{
			client = new DatagramSocket();
		}
		catch(SocketException e){
			System.out.println("Unable to open Client Socket!");
			e.printStackTrace();
			System.exit(1);
		}

		// Initialize retry_attempts
		for(int i=0; i<window_size; i++)
			retry_attempts[i] = 0;		
	}
}

/*
	Client Transmission Thread:
		Xmits pkts to the recvr
*/
class ClientXmitter extends Thread{

	// Contains shared data
	volatile SharedData s;

	// Contains Packets for xmission and reception
	DatagramPacket data_packet; 
	DatagramPacket ack_packet; 

	public ClientXmitter(SharedData sdata){
		s = sdata;

		// Build Packet Objects
		InetAddress IPAddress; 
		try{
			IPAddress = InetAddress.getByName(s.recvr);
			data_packet = new DatagramPacket(new byte[s.pkt_len], s.pkt_len, IPAddress, s.port);
			ack_packet  = new DatagramPacket(new byte[s.ack_len], s.ack_len);
		}
		catch (UnknownHostException e) {
			System.out.println("Unknown Host!");
			e.printStackTrace();
			System.exit(1);
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

				/*
				if(true){
					System.out.println("Timeout at " + Integer.toString(timeout_pkt));
					System.exit(1);
				}
				*/

				// rexmitt and increment retries
				for(int i=s.seq_beg; i != (timeout_pkt + 1) % s.window_size; i = (i+1)%s.window_size){				
					synchronized(s){
	
						// Check if not exceeded max retries
						if(s.retry_attempts[i] == 5){
							System.out.println("Exceeded Max No of Retries for Retransmission for seq # " + Integer.toString(i) + "!");
														
							if(s.deep_debug){
								System.out.println("Average RTT " + Double.toString(s.avg_rtt) + "!");
								for(int j=0; j<s.window_size; j++)
									System.out.println("Seq # " + Integer.toString(j) + " Retried " + 
														Integer.toString(s.retry_attempts[j]) + " times");								
							}

							// Print end info
							double rexmit_ratio = ((s.totalRetries + s.totalXmitPkts)*1.0) / (s.buf.acked_pkts);
							long millis = (long) s.avg_rtt;
							long micros = ((long) (s.avg_rtt * 1000)) % 1000;

							System.out.println("\n\nPktRate = " + Integer.toString(s.pkt_gen_rate) 
								+ ", Length = " + Integer.toString(s.pkt_len)
								+ ", Retran Ratio = " + Double.toString(rexmit_ratio)
								+ ", Avg RTT: " + Long.toString(millis) + ":" + Long.toString(micros));
							System.exit(1);
						}

						s.timer_tasks[i].cancel();

						// Compute new timeout time
						double timeout_time;
						if(s.no_of_pkts_sent < 10){
							timeout_time = 100.0;
						}
						else{
							timeout_time = Math.ceil(2*s.avg_rtt);
						}

						// Increment retrial attempts
						s.retry_attempts[i]++;
						s.totalRetries++;

						// ReXmitt
						DatagramPacket retry_data_packet;
						try{
							InetAddress IPAddress = InetAddress.getByName(s.recvr);
							retry_data_packet = new DatagramPacket(new byte[s.pkt_len], s.pkt_len, IPAddress, s.port);
							retry_data_packet.setData(s.sent_buffer[i]);
							s.client.send(retry_data_packet);
						}
						catch (UnknownHostException e) {
							System.out.println("Unknown Host Given!");
							e.printStackTrace();
							System.exit(1);
						}
						catch(IOException e){
							System.out.println("Unable to rexmit data pkt!");
							e.printStackTrace();
							System.exit(1);							
						}
						
						// Start timer
						s.timer_tasks[i] = new myTimerTask(timeout_pkt); 
						s.timer_thread.schedule(s.timer_tasks[i], (long) timeout_time);
						// s.timer_timestamps[i] = System.nanoTime();					

						if(s.deep_debug)
							System.out.println("Timer started at " + Long.toString(s.timer_timestamps[i]) + " for going off in " +
											Long.toString((long) timeout_time));
					}
				}
			}
		}

		if(s.deep_debug)
			System.out.println("Started Client Transmission Thread!");

		// If pkt count not reached
		while(s.buf.acked_pkts < s.max_pkts){
			synchronized(s){
				// If buffer is non-empty, iterate
				if(!s.buf.is_empty() && s.seq_left > 0){

					// Compute timeout time
					double timeout_time;
					if(s.no_of_pkts_sent < 10){
						timeout_time = 100.0;
					}
					else{
						timeout_time = Math.ceil(2*s.avg_rtt);
						if(s.deep_debug)
							System.out.println("Timeout in " + Long.toString((long)timeout_time));
					}

					// Setup current pkt
					byte[] curr = s.buf.pop();
					s.sent_buffer[s.seq_next] = curr;
					s.retry_attempts[s.seq_next] = 0;
					s.totalXmitPkts++;					

					data_packet.setData(curr);
					try{
						s.client.send(data_packet);
					}
					catch (IOException e) {
						System.out.println("IOException while sending data pkt!");
						e.printStackTrace();
						System.exit(1);
					}

					// Set timeout timer
					s.timer_tasks[s.seq_next] = new myTimerTask(s.seq_next);
					s.timer_thread.schedule(s.timer_tasks[s.seq_next], (long) timeout_time);
					s.timer_timestamps[s.seq_next] = System.nanoTime();

					// System.out.println("scheduled at " + Long.toString(s.timer_tasks[s.seq_next].scheduledExecutionTime()));

					if(s.deep_debug)
						System.out.println("Xmitted " + Integer.toString(s.seq_next));	

					// update seq_next
					s.seq_next = (s.seq_next + 1) % s.window_size;
					s.seq_left --;
					s.no_of_pkts_sent ++;
				}
				// Else reiterate
			}
		}
		
		// Close socket
		s.client.close();
	}
}

/*
	Client Receiver Thread:
		Recvs ACK pkts from the recvr
*/
class ClientRecvr extends Thread{
	
	// Contains shared data
	volatile SharedData s;

	// Contains Packets for xmission and reception
	DatagramPacket data_packet; 
	DatagramPacket ack_packet; 

	public ClientRecvr(SharedData sdata){
		s = sdata;

		// Build Packet Objects
		InetAddress IPAddress; 
		try{
			IPAddress = InetAddress.getByName(s.recvr);
			data_packet = new DatagramPacket(new byte[s.pkt_len], s.pkt_len, IPAddress, s.port);
			ack_packet  = new DatagramPacket(new byte[s.ack_len], s.ack_len);
		}
		catch (UnknownHostException e) {
			System.out.println("Unknown Host!");
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void run(){
		if(s.deep_debug)
			System.out.println("Started Client Receiver Thread!");

		while(s.buf.acked_pkts < s.max_pkts){
			// Receive ACK pkt
			try{
				s.client.receive(ack_packet);
			}
			catch(IOException e){
				System.out.println("IOException while receiving ACK pkt!");
				e.printStackTrace();
				System.exit(1);
			}

			synchronized(s){
				// Get sequence #
				int ack_seq = (ack_packet.getData()[0] << 24) + (ack_packet.getData()[1] << 16) + (ack_packet.getData()[2] << 8) 
									+ ack_packet.getData()[3];
				
				double timetaken = 0;
				// Ack all till ack_seq
				for(int i=s.seq_beg; i != (ack_seq)%s.window_size; i=(i+1)%s.window_size){
					
					if(s.deep_debug)
						System.out.println("Canceling " + Integer.toString(i));
					
					// cancel timer
					s.timer_tasks[i].cancel();
					
					// check time
					timetaken = System.nanoTime() - s.timer_timestamps[i];
					timetaken /= 1000000;

					// update rtt
					s.avg_rtt = ((s.avg_rtt * s.buf.acked_pkts) + timetaken)/(s.buf.acked_pkts + 1);
					s.buf.acked_pkts ++;

					// update windows vars
					s.seq_beg = (s.seq_beg + 1) % s.window_size;
					s.seq_end = (s.seq_end + 1) % s.window_size;
					s.seq_left++;
				}

				if(s.debug){
					int curr_seq = (ack_seq - 1) % s.window_size;
					if(curr_seq < 0)	curr_seq += s.window_size;

					System.out.println("Seq #: " + Integer.toString(curr_seq) + 
						" Time Generated: " + Long.toString((s.timer_timestamps[curr_seq]-s.startTime) / 1000000) + ":" 
											 + Long.toString(((s.timer_timestamps[curr_seq]-s.startTime) / 1000) % 1000) + 
						" RTT: " + Double.toString(timetaken) + 
						" Number of Attempts: " + Integer.toString(s.retry_attempts[curr_seq]));
				}

				if(s.deep_debug){
					System.out.println("# of Acked pkts = " + Integer.toString(s.buf.acked_pkts));
					System.out.println("Begin" + Integer.toString(s.seq_beg));
					System.out.println("Next " + Integer.toString(s.seq_next));
					System.out.println("End " + Integer.toString(s.seq_end));					
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
		System.exit(1);
	}

	public static void main(String[] args){
		// Instance Parameters initialized with default values
		String recvr 	 = "localhost";
		int port 		 = 1080;
		int pkt_len 	 = 1500;
		int ack_len 	 = 40;
		int pkt_gen_rate = 10;
		int max_pkts 	 = 1024;
		int window_size  = 8;
		int max_buf_size = 24;
		boolean debug 	 = false;
		boolean deep_debug 	 = false;

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
				else if(arg.equals("-dd"))
					deep_debug = true;
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
		Buffer buf = new Buffer(max_buf_size, window_size);

		// Initialize Shared Data
		SharedData s = new SharedData(buf, recvr, port, pkt_len, max_pkts, window_size, debug, ack_len, deep_debug, pkt_gen_rate);
		
		// Create thread objects
		PacketGenerator p = new PacketGenerator(buf, pkt_len, max_pkts, pkt_gen_rate);
		ClientXmitter sendThread = new ClientXmitter(s);
		ClientRecvr   recvThread = new ClientRecvr(s);

		if(s.deep_debug)
			System.out.println("Starting Threads!");

		// Start Threads
		p.start();
		sendThread.start();
		recvThread.start();

		// Wait for all threads to finish
		try{
			p.join();
			sendThread.join();
			recvThread.join();			
		}
		catch(InterruptedException e){
			System.out.println("Interrupted Thread Exception Encountered");
			e.printStackTrace();
			System.exit(1);
		}

		// Print end info
		double rexmit_ratio = ((s.totalRetries + s.totalXmitPkts)*1.0) / (buf.acked_pkts);
		long millis = (long) s.avg_rtt;
		long micros = ((long) (s.avg_rtt * 1000)) % 1000;

		if(deep_debug)
			System.out.println("Total xmits: " + Long.toString(s.totalRetries + s.totalXmitPkts) + ", Total acks: " + Long.toString(buf.acked_pkts));

		System.out.println("\n\nPktRate = " + Integer.toString(p.pkt_gen_rate) 
			+ ", Length = " + Integer.toString(s.pkt_len)
			+ ", Retran Ratio = " + Double.toString(rexmit_ratio)
			+ ", Avg RTT: " + Long.toString(millis) + ":" + Long.toString(micros));
		System.exit(1);
	}
}
