/*
	Program for receiving data pkts in Go-Back-N Nimulation

	Written by Rahul Kejriwal
*/

import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;

/*
	Recvr Class:
		Recvs data pkts and ACKs
*/
class GBNServer{

	// Recvr Parameters
	int port, max_pkts;
	double pkt_err_rate;
	boolean debug = false;

	// Assumption: These are also sent
	int pkt_len;
	int window_size;
	int ack_len = 40;

	// Internal Server Stuff
	DatagramSocket server;
	DatagramPacket data_packet; 
	DatagramPacket ack_packet; 

	// Local State Vars 
	long startTime;

	/*
		Configures and starts server/recvr.
	*/
	public GBNServer(int p, int n, double per, boolean d, int pl, int ws){
		
		// Store Params
		port = p;
		max_pkts = n;
		pkt_err_rate = per;
		debug = d;

		// Assumed Vars
		pkt_len = pl;
		window_size = ws;

		try{
			// Build Server Socket
			server = new DatagramSocket(port);
			
			// Build Packet Objects
			data_packet = new DatagramPacket(new byte[pkt_len], pkt_len);			
			ack_packet  = new DatagramPacket(new byte[ack_len], ack_len);

			// Initialize Start time
			startTime = System.nanoTime();
	
			// Sanity Check Message
			System.out.println("Receiver up at port " + Integer.toString(port));

			// Start Server
			run_server();
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}

	/*
		Function to generate bool with pkt_err_rate probability.
		Used to randomly decide pkt errors.
	*/
	boolean genProb(){
		double test_val = Math.random();

		if(test_val <= pkt_err_rate)
			return true;
		else
			return false;
	}

	/*
		Actual Server:
			Receives data pkts and xmits ACKs from here. 
	*/
	void run_server() throws IOException{
		
		// Internal State Variables
		int NFE =  0;
		int LFR = -1;

		// Locals / Temp vars
		int frame_seq = -1;
		long currTime;

		// Recv as long as max_pkts not received properly
		for(int pkt = 0; pkt < max_pkts; pkt++){
			
			// Receive a pkt
			// System.out.println("Waiting for Msg Pkt!");
			server.receive(data_packet);
			// System.out.println("Received a Msg Pkt!");
			
			// Simulate Network Errors
			boolean corrupt = genProb();
			if(corrupt){
				pkt--;
				continue;
			}	

			// Get frame seq #
			byte[] data = data_packet.getData();
			frame_seq = ((int)data[0] << 24) | ((int)data[1] << 16) | ((int)data[2] << 8) | ((int)data[3]);

			// Check whether to drop pkt
			boolean drop = false;
			if(frame_seq != NFE){
				drop = true;
				pkt--;
			}

			// Else update state vars
			else{
				LFR  = frame_seq;
				NFE  = (NFE + 1) % window_size;
			}

			// Calculate offset time
			currTime = System.nanoTime() - startTime;

			// Print DEBUG msgs
			if(debug){
				System.out.println("Seq #: " + frame_seq +
				 " Time Received: " + Long.toString((currTime/1000000)) + ":" + Long.toString((currTime/1000)%1000) + 
				 " Packet dropped: " + drop);
			}

			// Send ACK pkt
			if(!drop){
				// Fit the cummulative ack seq #
				byte[] ack_msg = new byte[ack_len];
				ack_msg[0] = (byte) (NFE >> 24);
				ack_msg[1] = (byte) (NFE >> 16);
				ack_msg[2] = (byte) (NFE >> 8);
				ack_msg[3] = (byte) NFE;

				// Build ack pkt
				ack_packet.setData(ack_msg); 
				ack_packet.setAddress(data_packet.getAddress());
				ack_packet.setPort(data_packet.getPort());

				// Send ACK
				server.send(ack_packet);
			}
		}
	}
}

/*
	Main Class:
		Parse cmd line args and start Recvr
*/
public class receiver{
	static void errorExit(String s){
		System.out.println("Error: " + s);
		System.exit(1);
	}

	public static void main(String[] args){

		// Instance Parameters (Default Values)
		int port = 1080;
		int max_pkts = 1024;
		int pkt_len  = 1500;
		int window_size = 8;
		double pkt_err_rate = 0.2;
		boolean debug = false;

		// Process Command Line Args
		int next_arg = 0;
		for(String arg: args){
			if(next_arg == 0){
				if(arg.equals("-d"))
					debug = true;
				else if(arg.equals("-p"))
					next_arg = 1;
				else if(arg.equals("-n"))
					next_arg = 2;
				else if(arg.equals("-e"))
					next_arg = 3;
				else if(arg.equals("-l"))
					next_arg = 4;
				else if(arg.equals("-w"))
					next_arg = 5;
				else
					errorExit("Incorrect Usage!");
			}
			else{
				switch(next_arg){
					case 1: port = Integer.parseInt(arg);
							break;

					case 2: max_pkts = Integer.parseInt(arg);
							break;

					case 3: pkt_err_rate = Double.parseDouble(arg);
							break;

					case 4: pkt_len = Integer.parseInt(arg);
							break;

					case 5: window_size = Integer.parseInt(arg);
							break;

					default: errorExit("Incorrect Usage!");
				}
				next_arg = 0;
			}
		}

		// Begin and Run Server
		GBNServer server = new GBNServer(port, max_pkts, pkt_err_rate, debug, pkt_len, window_size);
	}
}