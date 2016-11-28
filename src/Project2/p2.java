package Project2;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class p2 {

	static int id=1;
	static List<Peer> peers=new ArrayList<>();
	static Selector read;
	static Selector write;
	static List<SocketChannel> openChannels=new ArrayList<>();
	static String myip;
	static Client client;
	static Server server;
	static int time;
	static Map<Integer, Integer> neighbors=new HashMap<>();
	static Map<Integer, Peer> nodes=new HashMap<>();
	static int countNoOfMessageReceived;
	static int numOfUpdaedField;
	static int myport;
	
	public static void main(String args[]) throws IOException
	{
		read=Selector.open();
		write =Selector.open();
		server=new Server(Integer.parseInt(args[0]));
		server.start();
		client=new Client();
		client.start();
		
		Scanner input =new Scanner(System.in);
		boolean run= true;
		System.out.println("Welcom\n");
		
		
		// TODO Auto-generated method stub
		Timer timer = new Timer();		
		
		while(run)
		{
			timer.scheduleAtFixedRate(new TimerTask(){
				@Override
				public void run() {
				try {
					step();
				} catch (IOException e) {
					e.printStackTrace();
				}
				}
				}, time*1000, time*1000);
			
			String str = input.nextLine();
			String[] arrStr = str.split(" ");
			String command=arrStr[0];
			switch(command)
			{
			case "server":
				String fileName= arrStr[2];
				time= Integer.parseInt(arrStr[4]);
				readText(fileName);
				break;
			case "update":
				update(Integer.parseInt(arrStr[2]), Integer.parseInt(arrStr[3]));
				break;
			case "step":
				step();
				break;
			case "packets":
				System.out.println(countNoOfMessageReceived);
				break;
			case "display":
				display();
				break;
			case "disable":
				terminate(Integer.parseInt(arrStr[1]));
				break;
			case "crach":
				run =false;
				System.out.println("Bye!");
				timer.cancel();
				System.exit(1);
				break;
			}
		}
		input.close();
		
	}
	
	public static void readText(String fileName)
	{
		int myid=0;
		try{
			FileReader read = new FileReader(fileName);
			BufferedReader bf=new BufferedReader(read);
				int numServer=Integer.parseInt(bf.readLine());
				int numNeighbor=Integer.parseInt(bf.readLine());
				for(int i = 0;i<numServer;i++)
				{
					String[] str=bf.readLine().split(" ");
					int id=Integer.parseInt(str[0]);
					int port=Integer.parseInt(str[2]);
					nodes.put(id, new Peer(id, str[1], port));
					connect(str[1], port);
				}
				for(int i = 0;i<numNeighbor;i++)
				{
					String[] str=bf.readLine().split(" ");
					myid=Integer.parseInt(str[0]);
					neighbors.put(Integer.parseInt(str[1]), Integer.parseInt(str[2]));
					nodes.get(Integer.parseInt(str[1])).setCost(Integer.parseInt(str[2]));
					nodes.get(Integer.parseInt(str[1])).setPeerId(myid);
				}
				
				for(Integer cost: neighbors.values())
				{
					System.out.println(cost);
				}
				for(Peer peer:nodes.values())
				{
					System.out.println(peer.getIp());
				}
				myport=nodes.get(myid).getPort();
				myip=nodes.get(myid).getIp();
				
			bf.close();
		}catch(IOException e)
		{
			System.out.println("Something went wrong!...In the reading the text file");
		}
	}
	
	public static void update(int serverId2, int cost)
	{
		nodes.get(serverId2).setCost(cost);
		System.out.println("Update SUCCESS");
	}
	
	public static void step() throws IOException
	{
		Message message=new Message(numOfUpdaedField, myport, myip );
		List<Peer> peers=new ArrayList<>();
		for(Peer peer: nodes.values())
		{
			peers.add(new Peer(peer.getId(), peer.getIp(), peer.getPort(), peer.getCost(),peer.getPeerId()));
		}
		message.setNodes(peers);
		for(int neighbour:neighbors.keySet())
		{
			send(neighbour, message);
		}
		System.out.println("Step SUCCESS");
	}
	
	public static void display()
	{
		System.out.println("Display SUCCESS");
	}
	public static void connect(String ip,int port)
	{
		System.out.println("connecting to "+ip);
		try {
			if(!ip.equals(myip))
			{
				for(Peer peer:peers)
				{
					if(peer.getIp().equals(ip))
					{
						System.out.println(ip+" already connected");
						return;
					}
				}
				SocketChannel socketChannel = SocketChannel.open();
				socketChannel.connect(new InetSocketAddress(ip, port));
				socketChannel.configureBlocking(false);
				socketChannel.register(read, SelectionKey.OP_READ );
				socketChannel.register(write, SelectionKey.OP_WRITE );
				openChannels.add(socketChannel);
				id++;
				peers.add(new Peer(id,ip, port));
			}
			else
			{
				System.out.println("you can't connect to yourself or peer already connected");
			}
			
		} catch (NumberFormatException | IOException e) {
			System.out.println("error in connection");
		}
	}
	
	public static String parseChannelIp(SocketChannel channel){//parse the ip form the SocketChannel.getRemoteAddress();
		String ip = null;
		String rawIp =null;  
		try {
			rawIp = channel.getRemoteAddress().toString().split(":")[0];
			ip = rawIp.substring(1, rawIp.length());
		} catch (IOException e) {
			System.out.println("can't convert channel to ip");
		}
		return ip;
	}
	public static Integer parseChannelPort(SocketChannel channel){//parse the ip form the SocketChannel.getRemoteAddress();
		String port =null;  
		try {
			port = channel.getRemoteAddress().toString().split(":")[1];
		} catch (IOException e) {
			System.out.println("can't convert channel to ip");
		}
		return Integer.parseInt(port);
	}
	
	public static void send(Integer id, Message message) throws IOException
	{
		int channelReady=0;
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
		ObjectOutput out= null;
		try{
			out=new ObjectOutputStream(bos);
			out.writeObject(message);
			out.flush();
			byte[] bytes= bos.toByteArray();
			channelReady=write.select();
			if(channelReady>0)
			{
				Set<SelectionKey> keys=write.selectedKeys();
				Iterator<SelectionKey> selectedKeysIterator = keys.iterator();
				ByteBuffer buffer = ByteBuffer.allocate(Integer.MAX_VALUE);
				buffer.put(bytes);
				buffer.flip();
				
				while(selectedKeysIterator.hasNext())
				{
					SelectionKey selectionKey=selectedKeysIterator.next();
					if(parseChannelIp((SocketChannel)selectionKey.channel()).equals(getPeer(id).getIp()))
					{
						SocketChannel socketChannel=(SocketChannel)selectionKey.channel();
						socketChannel.write(buffer);
					}
					selectedKeysIterator.remove();
				}
			}
		}catch(Exception e)
		{
			System.out.println("sending fail because"+e.getMessage());
		}
		finally
		{
			bos.close();
		}
	}
	
	public static void terminate(Integer id)
	{
		try
		{
				Peer peer=getPeer(id);
				for(SocketChannel channel:openChannels)
				{
					if(peer.getIp().equals(parseChannelIp(channel)))
					{
						channel.close();
						openChannels.remove(channel);
						break;
					}
				}
				peers.remove(peer);
				System.out.println("terminated connection "+id);
			
		}catch(Exception e)
		{
			System.out.println("termination failed due to "+e.getMessage());
		}
	}
	public static Peer getPeer(int id)
	{
		for(Peer peer:peers)
		{
			if(peer.getId()==id)
			{
				return peer;
			}
		}
		return null;
	}
	public static Peer getIpFromPeers(String ip)
	{
		for(Peer peer:peers)
		{
			if(peer.getIp().equals(ip))
			{
				return peer;
			}
		}
		return null;
	}
	
}

class Server extends Thread {
	
	int port=0;
	
	Server(int port)
	{
		this.port=port;
	}
	public void run()
	{
		try {
			p2.read=Selector.open();
			p2.write=Selector.open();
			ServerSocketChannel serverSocketChannel=ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.bind(new InetSocketAddress(port));
			while(true)
			{
				SocketChannel socketChannel=serverSocketChannel.accept();
				if(socketChannel != null)
				{
					socketChannel.configureBlocking(false);
					socketChannel.register(p2.read, SelectionKey.OP_READ);
					socketChannel.register(p2.write, SelectionKey.OP_WRITE);
					p2.openChannels.add(socketChannel);
					int id=p2.id++;
					p2.peers.add(new Peer(id++,p2.parseChannelIp(socketChannel), p2.parseChannelPort(socketChannel)));
					System.out.println("The connection to peer "+p2.parseChannelIp(socketChannel)+" is succesfully established");
				}
			}
		}
		catch(BindException ex){
    		System.out.println("Can not use port " + this.port + ". Please choose another port and restart\n");
    		System.exit(1);
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
}

class Client extends Thread{
	
	Set<SelectionKey> keys;
	Iterator<SelectionKey> selectedKeysIterator;
	ByteBuffer buffer=ByteBuffer.allocate(1024);
	SocketChannel socketChannel;
	int byteRead;
	public void run()
	{
		try{
			while(true)
			{
				ByteArrayInputStream input= new ByteArrayInputStream(buffer.array());
				ObjectInput in=new ObjectInputStream(input);
				Message msg= (Message) in.readObject();
				String ip=msg.getIp();
				int neighbour=0;
				int neighbourId=0;
				for(Peer peer: p2.nodes.values())
				{
					if(peer.getIp().equals(ip))
					{
						neighbourId=peer.getId();
						neighbour=peer.getCost();
					}
				}
				for(Peer peer:msg.getNodes())
				{
					for(Integer id: p2.nodes.keySet())
					{
						if(id == peer.getId())
						{
							if((peer.getCost()+neighbour)<p2.nodes.get(id).getCost())
							{
								p2.nodes.get(id).setCost(peer.getCost()+neighbour);
								p2.nodes.get(id).setPeerId(neighbourId);
							}
						}
					}
				}
				
/*				int channelReady=p2.read.selectNow();
				keys=p2.read.selectedKeys();
				selectedKeysIterator=keys.iterator();
				if(channelReady != 0)
				{
					while(selectedKeysIterator.hasNext())
					{
						SelectionKey key=selectedKeysIterator.next();
						socketChannel=(SocketChannel)key.channel();
						try{
							byteRead=socketChannel.read(buffer);
						}catch(IOException e)
						{
							selectedKeysIterator.remove();
							String IP=p2.parseChannelIp(socketChannel);
							Peer peer=p2.getIpFromPeers(IP);
							p2.terminate(peer.getId());
							System.out.println(IP+" remotely close the connection");
							break;
						}
						String message="";
						while(byteRead != 0)
						{
							buffer.flip();
							while(buffer.hasRemaining())
							{
								message+=((char)buffer.get());
							}
							message=message.trim();
							if(message.isEmpty())
							{
								String IP=p2.parseChannelIp(socketChannel);
								Peer peer=p2.getIpFromPeers(IP);
								p2.terminate(peer.getId());
								System.out.println("Peer "+IP + " terminates the connection");	
								break;
							}
							else
							{
								System.out.println("Message received from "+p2.parseChannelIp(socketChannel)+": "+message);
							}
							buffer.clear();
							if(message.trim().isEmpty())
								byteRead =0;
							else
								byteRead = socketChannel.read(buffer);
						
							byteRead=0;
							
						}
						
						selectedKeysIterator.remove();
					}
				}*/
			}
			
		}catch(Exception e)
		{
			System.out.println("something wrong went client side");
		}
	}
}


class Peer{
	
	private int id;
	private String ip;
	private int port;
	private int cost=Integer.MAX_VALUE;
	private int peerId;
	
	Peer(int id,String ip, int port)
	{
		this.id=id;
		this.ip=ip;
		this.port=port;
	}
	Peer(int id, String ip, int port, int cost,int peerId)
	{
		this.id=id;
		this.ip=ip;
		this.port=port;
		this.cost=cost;
		this.peerId=peerId;
	}

	public int getId() {
		return id;
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}
	public int getCost() {
		return cost;
	}

	public void setCost(int cost) {
		this.cost = cost;
	}
	public int getPeerId() {
		return peerId;
	}
	public void setPeerId(int peerId) {
		this.peerId = peerId;
	}
	public void setIp(String ip) {
		this.ip = ip;
	}
	public void setPort(int port) {
		this.port = port;
	}
	
	
}

class Message implements Serializable{
	
	private static final long serialVersionUID = 1L;
	
	private int numOfUpdatedFields;
	private int port;
	private String ip;
	private List<Peer> nodes;
	
	Message(int numOfUpdatedFields, int port, String ip, List<Peer> nodes)
	{
		this.numOfUpdatedFields=numOfUpdatedFields;
		this.port=port;
		this.ip=ip;
		this.nodes=nodes;
	}
	Message(int numOfUpdatedFields, int port, String ip)
	{
		this.numOfUpdatedFields=numOfUpdatedFields;
		this.port=port;
		this.ip=ip;
	}

	public int getNumOfUpdatedFields() {
		return numOfUpdatedFields;
	}

	public void setNumOfUpdatedFields(int numOfUpdatedFields) {
		this.numOfUpdatedFields = numOfUpdatedFields;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}
	public List<Peer> getNodes() {
		return nodes;
	}
	public void setNodes(List<Peer> nodes) {
		this.nodes = nodes;
	}
}

