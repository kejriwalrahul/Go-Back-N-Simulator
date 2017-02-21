import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;

/*
	To Do:
		Initializa Params
		Try Serilialization
	
class MyPacket{
	int seq_no;
	byte[] data;

	MyPacket(int len){
		data = new byte[len];
	}
}
*/

/*
	Recvr Class:
		Recvs data pkts and ACKs
*/
class GBNServer{

	// Recvr Parameters
	int port, max_pkts;
	double pkt_err_rate;
	boolean debug = False;

	// In doubt
	int pkt_len;
	int ack_len = 40;
	int window_size = 255;

	// Server Stuff
	DatagramSocket server;
	DatagramPacket data_packet; 
	DatagramPacket ack_packet; 

	// Local State Vars 
	long startTime;

	/*	
	MyPacket data_pkt;
	*/

	/*
		Configures and starts server/recvr.
	*/
	GBNServer(int p, int n, double per, boolean d){
		
		// Store Params
		port = p;
		max_pkts = n;
		pkt_err_rate = per;
		debug = d;

		// In doubt
		pkt_len = 100;

		try{
			// Build Server
			server = new DatagramSocket(port);
			
			// data_pkt = new MyPacket(pkt_len);
			data_packet = new DatagramPacket(new byte[pkt_len], pkt_len);			
			ack_packet  = new DatagramPacket(new byte[ack_len], ack_len);

			// Initialize Start time
			startTime = System.nanoTime();

			run_server();
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}

	/*
		Function to generate bool with a given probability.
		Used to randomly decide pkt errors.
	*/
	boolean genProb(){
		double test_val = Math.random();

		if(test_val <= pkt_err_rate)
			return True;
		else
			return False;
	}

	/*
		Actual Server:
			Receives data pkts and xmits ACKs from here. 
	*/
	void run_server() throws IOException{
		// State Variables
		int NFE = 0;
		int LFR = -1;

		// Locals
		int frame_seq = -1;
		long currTime;

		// Recv as long as pkts arrive
		int pkt;
		for(pkt = 0; pkt < max_pkts; pkt++){
			
			// Receive a pkt
			server.receive(data_packet);
			
			// Simulate Network Errors
			boolean corrupt = genProb();
			if(corrupt)	
				continue;

			// Get frame seq #
			byte[] data = data_packet.getData();
			frame_seq = ((int)data[0] << 24) | ((int)data[1] << 16) | ((int)data[2] << 8) | ((int)data[3]);

			// Check whether to drop pkt
			boolean drop = false;
			if(frame_seq != NFE)
				drop = true;
			// Else update state vars
			else{
				LFR  = frame_seq;
				NFE  = (NFE + 1)%window_size;
			}

			// Calculate offset time
			currTime = System.nanoTime() - startTime;

			// Print DEBUG msgs
			if(debug){
				System.out.println("Seq #: " + frame_seq +
				 " Time Received: " + Long.toString((currTime/1000000)%1000) + ":" + Long.toString((currTime/1000)%1000) + 
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
	void errorExit(String s){
		System.out.println("Error: " + s);
		System.out.println("Error: " + s);
	}

	public static void main(String[] args){
		// Instance Parameters
		int port, max_pkts;
		double pkt_err_rate;
		boolean debug = False;

		// Process Command Line Args
		int next_arg = 0;
		for(String arg: args){
			if(next_arg == 0){
				if(arg.equals("-d"))
					debug = True;
				else if(arg.equals("-p"))
					next_arg = 1;
				else if(arg.equals("-n"))
					next_arg = 2;
				else if(arg.equals("-e"))
					next_arg = 3;
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

					default: errorExit("Incorrect Usage!");
				}
				next_arg = 0;
			}
		}

		// Begin and Run Server
		GBNServer server = new GBNServer(port, max_pkts, pkt_err_rate, debug);
	}
}