package database;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import kernel.tile;
import window.window;


public class server extends JFrame implements Runnable{
    /**
     *
     */
    private static final long serialVersionUID = -8874088438115985366L;
    private Connection con;


    public server() {
        Thread t = new Thread(this);
        t.start();
    }

    @SuppressWarnings("resource")
    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            // Create a server socket
            ServerSocket serverSocket = new ServerSocket(8000);
            con = DriverManager.getConnection("jdbc:sqlite:database.db");

            while (true) {
                // Listen for a new connection request
                Socket socket = serverSocket.accept();

                // Create and start a new thread for the connection
                new Thread(new HandleAClient(socket)).start();
            }
        }
        catch(IOException ex) {
            System.err.println(ex);
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


    }
    // Define the thread class for handling new connection
    class HandleAClient implements Runnable {
        private Socket socket; // A connected socket
        private String playername;
        private int highscore;
        DataOutputStream outputToClient;
        DataInputStream inputFromClient;

        /** Construct a thread */
        public HandleAClient(Socket socket) {
            this.socket = socket;
        }

        /** Run a thread */
        public void run() {

            try {
                // Create data input and output streams
                inputFromClient = new DataInputStream(socket.getInputStream());
                outputToClient = new DataOutputStream(socket.getOutputStream());

                switch(inputFromClient.readInt()) {
                    case 0:
                        updateHighScore();
                        break;
                    case 1:
                        showHighScore();
                        break;
                    case 2:
                        storeMap();
                        break;
                    case 3:
                        loadMap();
                        break;
                    case 4:
                        searchExistingGame();
                        break;
                    default:

                        break;
                }

            }
            catch(IOException |SQLException ex) {
                ex.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

        }

        private void updateHighScore() throws IOException, SQLException {
            playername = inputFromClient.readUTF();
            highscore = inputFromClient.readInt();

            String sql = "Select * from HighScore where score = (select min(score) from HighScore);";
            PreparedStatement statement = con.prepareStatement(sql);
            ResultSet rs = statement.executeQuery();
            int id = rs.getInt(3);
            int minscore = rs.getInt(2);
            if(highscore > minscore) {
                sql = "update HighScore "
                        + "set name = \"" + playername + "\", "
                        + "score = "+ highscore +" "
                        + "where id = " + id + ";";
                Statement update = con.createStatement();
                update.execute(sql);
            }
        }

        private void showHighScore() throws SQLException, IOException {
            String sql = "Select * from HighScore order by score desc";
            PreparedStatement statement = con.prepareStatement(sql);
            ResultSet rs = statement.executeQuery();
            String str = "";
            while(rs.next() && !rs.isAfterLast())
            {
                str += (rs.getString(1) + "\t" + rs.getInt(2) + "\n");
            }
            outputToClient.writeUTF(str);
            outputToClient.flush();
        }

        private void storeMap() throws IOException, ClassNotFoundException, SQLException {
            ObjectInputStream objectIn = new ObjectInputStream(socket.getInputStream());
            Object deSerializedObject = objectIn.readObject();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream tmp = null;
            tmp = new ObjectOutputStream(bos);
            tmp.writeObject(deSerializedObject);
            tmp.flush();

            byte[] map = bos.toByteArray();

            String gameName = inputFromClient.readUTF();
            int time = inputFromClient.readInt();
            int flagleft = inputFromClient.readInt();

            String sq = "Select time from maptable where gamename = \"" + gameName + "\";";
            PreparedStatement search = con.prepareStatement(sq);
            ResultSet rs = search.executeQuery();
            if(rs.next()) {
                String sql = "update maptable set "
                        + "gamename = \""+ gameName +"\",gamemap = ?,time = " + time
                        + ",flag = " + flagleft
                        +" where gamename = \"" + gameName +"\";";
                PreparedStatement statement = con.prepareStatement(sql);
                statement.setBytes(1, map);
                statement.execute();
            }else {
                String sql = "insert into maptable  values("
                        + "\""+ gameName +"\",?," + time
                        + "," + flagleft
                        +");";
                PreparedStatement statement = con.prepareStatement(sql);
                statement.setBytes(1, map);
                statement.execute();
            }
        }

        private void loadMap() throws SQLException, IOException, ClassNotFoundException {
            DataOutputStream toClient = new DataOutputStream(socket.getOutputStream());
            DataInputStream fromClient = new DataInputStream(socket.getInputStream());

            String s = fromClient.readUTF();

            String sql = "select * from maptable where gamename = \""+s+"\";";
            PreparedStatement statement = con.prepareStatement(sql);
            ResultSet rs = statement.executeQuery();

            if(rs.next() && !rs.isAfterLast())
            {
                byte[] list = rs.getBytes(2);
                int time = rs.getInt(3);
                int flag = rs.getInt(4);

                toClient.writeBoolean(true);

                ObjectInputStream objectIn = null;

                if (list != null)
                    objectIn = new ObjectInputStream(new ByteArrayInputStream(list));

                Object mapState = objectIn.readObject();
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(mapState);
                out.flush();
                toClient.writeInt(time);
                toClient.flush();
                toClient.writeInt(flag);
                toClient.flush();
            }else {
                toClient.writeBoolean(false);
            }
        }

        private void searchExistingGame() throws SQLException, IOException {
            String sql = "Select gamename from maptable;";
            PreparedStatement statement = con.prepareStatement(sql);
            ResultSet rs = statement.executeQuery();
            for(int i = 0; i < 5; i++) {
                if(rs.next() && !rs.isAfterLast()) {
                    String str = rs.getString(1);
                    outputToClient.writeBoolean(true);
                    outputToClient.flush();
                    outputToClient.writeUTF(str);
                    outputToClient.flush();
                }
            }
            outputToClient.writeBoolean(false);
            outputToClient.flush();
        }

    }

    public static void main (String[] args){
        new server();
    }

}


