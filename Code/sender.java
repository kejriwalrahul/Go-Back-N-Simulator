import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.*;

/*
	To Do:
		Initializa Params
		Send params to classes
*/

/*
	Buffer class:
		Stores shared buffer between PacketGenerator and ClientXmitter
*/
class Buffer{
	public int max_buf_size;
	public Queue<byte[]> buf = new Queue<byte[]>();

	void push(byte[] buf){
		if(buf.size() >= max_buf_size)
			return;

		buf.add(buf);
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
	Buffer buf;

	// Generate Packets and place in buffer
	public void run(){

	}
}

/*
	Client Transmission Thread:
		Xmits pkts to the recvr
*/
class ClientXmitter extends Thread{
	// Buffer object
	Buffer buf;

	// Xmit Packets from buffer
	public void run(){

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
		ClientXmitter c = new ClientXmitter(buf);
		PacketGenerator p = new PacketGenerator(buf);

		// Start Threads
		c.start();
		p.start();
	}
}
