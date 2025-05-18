package com.home.jdetect;

import com.jlibrosa.audio.JLibrosa;
import com.google.gson.Gson;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class MFCC {
    public static void main(String[] args) {
        int port = 5555; // You can change the port if needed
        int nMFCC = 40;
        int n_fft = 400;
        int hop_length = 160;
        int n_mels = 64;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("MFCC Daemon started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Handle each client in a new thread if you want concurrency
                new Thread(() -> handleClient(clientSocket, nMFCC, n_fft, hop_length, n_mels)).start();
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket, int nMFCC, int n_fft, int hop_length, int n_mels) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            Gson gson = new Gson();
            JLibrosa jLibrosa = new JLibrosa();

            String line;
            StringBuilder jsonInput = new StringBuilder();
            while (!clientSocket.isClosed() && (line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    // End of one JSON input, process it
                    if (jsonInput.length() > 0) {
                        InputData inputData = gson.fromJson(jsonInput.toString(), InputData.class);
                        float[] audioFeatureValues = new float[inputData.waveform.length];
                        for (int i = 0; i < inputData.waveform.length; i++) {
                            audioFeatureValues[i] = inputData.waveform[i].floatValue();
                        }

                        float[][] mfccValues = jLibrosa.generateMFCCFeatures(
                                audioFeatureValues,
                                inputData.sample_rate,
                                nMFCC,
                                n_fft,
                                n_mels,
                                hop_length
                        );

                        String mfccJson = gson.toJson(mfccValues);
                        out.write(mfccJson);
                        out.newLine();
                        out.flush();
                        jsonInput.setLength(0); // Clear for next input
                    }
                } else {
                    jsonInput.append(line);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignore) {}
        }
    }

    // Helper class for JSON input
    static class InputData {
        public Double[] waveform;
        public int sample_rate;
    }
}