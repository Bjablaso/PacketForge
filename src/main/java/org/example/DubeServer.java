
package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DubeServer {

    private static final int SERVER_PORT_NUMBER = 21211;
    private static final int MAX_FRAGMENT_SIZE_IN_BYTES = 1000;
    private static final String WEBSITE_URL = "https://google.com";

    public static void main(String[] args) {

        HttpClient websiteHttpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest websiteDownloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(WEBSITE_URL))
                .build();

        byte[] downloadedWebsiteData;

        try {
            Instant downloadStartTime = Instant.now();

            HttpResponse<byte[]> websiteDownloadResponse =
                    websiteHttpClient.send(
                            websiteDownloadRequest,
                            HttpResponse.BodyHandlers.ofByteArray()
                    );

            Instant downloadEndTime = Instant.now();

            downloadedWebsiteData = websiteDownloadResponse.body();

            long totalDownloadTimeInMilliseconds =
                    Duration.between(downloadStartTime, downloadEndTime).toMillis();

            System.out.println("time: " + totalDownloadTimeInMilliseconds + " ms");

        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException(
                    "Error sending request to " + WEBSITE_URL,
                    exception
            );
        }

        List<byte[]> websiteDataFragments = new ArrayList<>();

        for (int currentByteOffset = 0;
             currentByteOffset < downloadedWebsiteData.length;
             currentByteOffset += MAX_FRAGMENT_SIZE_IN_BYTES) {

            int endingByteIndex = Math.min(
                    currentByteOffset + MAX_FRAGMENT_SIZE_IN_BYTES,
                    downloadedWebsiteData.length
            );

            byte[] currentWebsiteFragment = Arrays.copyOfRange(
                    downloadedWebsiteData,
                    currentByteOffset,
                    endingByteIndex
            );

            websiteDataFragments.add(currentWebsiteFragment);
        }

        int totalNumberOfFragments = websiteDataFragments.size();

        int lastFragmentSizeInBytes =
                (totalNumberOfFragments == 0)
                        ? 0
                        : websiteDataFragments.get(totalNumberOfFragments - 1).length;

        System.out.println("last: " + lastFragmentSizeInBytes + " bytes");

        try (DatagramSocket serverUdpSocket =
                     new DatagramSocket(SERVER_PORT_NUMBER)) {

            System.out.println(
                    "Listening on UDP port " + SERVER_PORT_NUMBER + "..."
            );

            byte[] incomingClientMessageBuffer = new byte[1024];

            DatagramPacket incomingClientTimeoutPacket =
                    new DatagramPacket(
                            incomingClientMessageBuffer,
                            incomingClientMessageBuffer.length
                    );

            serverUdpSocket.receive(incomingClientTimeoutPacket);

            InetAddress connectedClientIpAddress =
                    incomingClientTimeoutPacket.getAddress();

            int connectedClientPortNumber =
                    incomingClientTimeoutPacket.getPort();

            String clientTimeoutValueAsText = new String(
                    incomingClientTimeoutPacket.getData(),
                    0,
                    incomingClientTimeoutPacket.getLength(),
                    StandardCharsets.UTF_8
            ).trim();

            int clientTimeoutInMilliseconds =
                    Integer.parseInt(clientTimeoutValueAsText);

            System.out.println(
                    "timeout: " + clientTimeoutInMilliseconds + " ms"
            );

            System.out.println(
                    "client: " + connectedClientIpAddress.getHostAddress()
                            + "/" + connectedClientPortNumber
            );

            byte[] totalFragmentCountAsBytes =
                    String.valueOf(totalNumberOfFragments)
                            .getBytes(StandardCharsets.UTF_8);

            DatagramPacket totalFragmentCountPacket =
                    new DatagramPacket(
                            totalFragmentCountAsBytes,
                            totalFragmentCountAsBytes.length,
                            connectedClientIpAddress,
                            connectedClientPortNumber
                    );

            serverUdpSocket.send(totalFragmentCountPacket);

            for (int currentFragmentNumber = 1;
                 currentFragmentNumber <= totalNumberOfFragments;
                 currentFragmentNumber++) {

                byte[] currentFragmentData =
                        websiteDataFragments.get(currentFragmentNumber - 1);

                ByteBuffer fragmentPacketByteBuffer =
                        ByteBuffer.allocate(4 + currentFragmentData.length);

                fragmentPacketByteBuffer.putInt(currentFragmentNumber);
                fragmentPacketByteBuffer.put(currentFragmentData);

                byte[] completeFragmentPacketData =
                        fragmentPacketByteBuffer.array();

                DatagramPacket outgoingFragmentPacket =
                        new DatagramPacket(
                                completeFragmentPacketData,
                                completeFragmentPacketData.length,
                                connectedClientIpAddress,
                                connectedClientPortNumber
                        );

                serverUdpSocket.send(outgoingFragmentPacket);
            }

            serverUdpSocket.setSoTimeout(clientTimeoutInMilliseconds);

            byte[] acknowledgementMessageBuffer = new byte[1024];

            DatagramPacket incomingAcknowledgementPacket =
                    new DatagramPacket(
                            acknowledgementMessageBuffer,
                            acknowledgementMessageBuffer.length
                    );

            try {
                serverUdpSocket.receive(incomingAcknowledgementPacket);

                System.out.println("ack received");

            } catch (SocketTimeoutException exception) {
                System.out.println("timeout");
            }

        } catch (IOException exception) {
            throw new RuntimeException("UDP error", exception);

        } catch (NumberFormatException exception) {
            throw new RuntimeException(
                    "Client did not send a valid timeout value",
                    exception
            );
        }
    }
}

