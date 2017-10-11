import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import java.io.*;
import java.net.*;

import java.io.IOException;

/**
 * Created by Macken on 2017-10-06.
 */
public class ImageServer implements Runnable {
  private ServerSocket welcomeSocket;
  private Process cameraProcess;
  private boolean stopFlagged;
  private ALCRegulator ALCreg;
  private Socket connectionSocket;


  public ImageServer(ALCRegulator ALCreg) throws IOException {
    this.ALCreg = ALCreg;
    String argv = "raspivid -l -o tcp://0.0.0.0:2222 --framerate 10 -w 1920 -h 432 -t 0";
    cameraProcess = Runtime.getRuntime().exec(argv);
    System.out.println("hej");
    welcomeSocket = new ServerSocket(2223);
    System.out.println("hej");
  }



  public void receive() throws IOException {
    BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
    String offset = inFromClient.readLine();
    System.out.println("Received: " + offset);
    ALCreg.calcSteering(Integer.parseInt(offset));

  }


  @Override
  public void run() {
    try {
      System.out.println("trying to acc");
      connectionSocket = null;
      System.out.println("acc");
      connectionSocket = welcomeSocket.accept();
      System.out.println("acc");

      while(connectionSocket.isConnected()) {
        receive();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
