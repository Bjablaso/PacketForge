
package org.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class DubeClient {

    private static final int SERVER_PORT_NUMBER = 21211;
    private static final int MAX_FRAGMENT_PACKET_SIZE_IN_BYTES = 1000 + 4;
    private static final int MAX_RECEIVE_WAIT_TIME_IN_MILLISECONDS = 5000;

    public static void main(String[] args) {

        String serverHostName =
                args.length > 0 ? args[0] : "localhost";

        int clientTimeoutInMilliseconds =
                args.length > 1 ? Integer.parseInt(args[1]) : 2000;

        int acknowledgementDelayInMilliseconds =
                args.length > 2 ? Integer.parseInt(args[2]) : 0;

        try (DatagramSocket clientUdpSocket = new DatagramSocket()) {

            InetAddress serverIpAddress =
                    InetAddress.getByName(serverHostName);

            clientUdpSocket.setSoTimeout(
                    MAX_RECEIVE_WAIT_TIME_IN_MILLISECONDS
            );

            byte[] clientTimeoutMessageBytes =
                    String.valueOf(clientTimeoutInMilliseconds)
                            .getBytes(StandardCharsets.UTF_8);

            DatagramPacket outgoingTimeoutPacket =
                    new DatagramPacket(
                            clientTimeoutMessageBytes,
                            clientTimeoutMessageBytes.length,
                            serverIpAddress,
                            SERVER_PORT_NUMBER
                    );

            clientUdpSocket.send(outgoingTimeoutPacket);

            System.out.println(
                    "Sent timeout " + clientTimeoutInMilliseconds
                            + " ms to " + serverHostName
                            + ":" + SERVER_PORT_NUMBER
            );

            byte[] incomingServerPacketBuffer =
                    new byte[MAX_FRAGMENT_PACKET_SIZE_IN_BYTES];

            DatagramPacket incomingFragmentCountPacket =
                    new DatagramPacket(
                            incomingServerPacketBuffer,
                            incomingServerPacketBuffer.length
                    );

            clientUdpSocket.receive(incomingFragmentCountPacket);

            String totalFragmentCountAsText = new String(
                    incomingFragmentCountPacket.getData(),
                    0,
                    incomingFragmentCountPacket.getLength(),
                    StandardCharsets.UTF_8
            ).trim();

            int totalNumberOfExpectedFragments =
                    Integer.parseInt(totalFragmentCountAsText);

            System.out.println(
                    "k: " + totalNumberOfExpectedFragments
            );

            byte[][] receivedWebsiteFragments =
                    new byte[totalNumberOfExpectedFragments + 1][];

            int totalNumberOfReceivedFragments = 0;

            try {

                while (totalNumberOfReceivedFragments
                        < totalNumberOfExpectedFragments) {

                    DatagramPacket incomingWebsiteFragmentPacket =
                            new DatagramPacket(
                                    incomingServerPacketBuffer,
                                    incomingServerPacketBuffer.length
                            );

                    clientUdpSocket.receive(
                            incomingWebsiteFragmentPacket
                    );

                    ByteBuffer fragmentDataByteBuffer =
                            ByteBuffer.wrap(
                                    incomingWebsiteFragmentPacket.getData(),
                                    0,
                                    incomingWebsiteFragmentPacket.getLength()
                            );

                    int receivedFragmentNumber =
                            fragmentDataByteBuffer.getInt();

                    byte[] receivedFragmentData =
                            new byte[fragmentDataByteBuffer.remaining()];

                    fragmentDataByteBuffer.get(receivedFragmentData);

                    if (receivedFragmentNumber >= 1
                            && receivedFragmentNumber
                            <= totalNumberOfExpectedFragments
                            && receivedWebsiteFragments[receivedFragmentNumber]
                            == null) {

                        receivedWebsiteFragments[receivedFragmentNumber] =
                                receivedFragmentData;

                        totalNumberOfReceivedFragments++;

                        System.out.println(
                                "fragment " + receivedFragmentNumber
                                        + ": " + receivedFragmentData.length
                                        + " bytes"
                        );
                    }
                }

            } catch (SocketTimeoutException exception) {

                System.out.println(
                        "Stopped waiting for fragments after "
                                + MAX_RECEIVE_WAIT_TIME_IN_MILLISECONDS
                                + " ms"
                );
            }

            ByteArrayOutputStream completeWebsiteData =
                    new ByteArrayOutputStream();

            StringBuilder missingFragmentNumbers =
                    new StringBuilder();

            for (int currentFragmentNumber = 1;
                 currentFragmentNumber <= totalNumberOfExpectedFragments;
                 currentFragmentNumber++) {

                if (receivedWebsiteFragments[currentFragmentNumber] != null) {

                    completeWebsiteData.write(
                            receivedWebsiteFragments[currentFragmentNumber]
                    );

                } else {

                    missingFragmentNumbers
                            .append(currentFragmentNumber)
                            .append(' ');
                }
            }

            System.out.println(
                    "received " + totalNumberOfReceivedFragments
                            + "/" + totalNumberOfExpectedFragments
                            + " fragments, "
                            + completeWebsiteData.size()
                            + " bytes total"
            );

            if (missingFragmentNumbers.length() > 0) {

                System.out.println(
                        "missing: "
                                + missingFragmentNumbers.toString().trim()
                );
            }

            if (totalNumberOfExpectedFragments > 0
                    && receivedWebsiteFragments[totalNumberOfExpectedFragments]
                    != null) {

                System.out.println(
                        "last: "
                                + receivedWebsiteFragments[
                                totalNumberOfExpectedFragments
                                ].length
                                + " bytes"
                );
            }

            if (acknowledgementDelayInMilliseconds > 0) {

                Thread.sleep(
                        acknowledgementDelayInMilliseconds
                );
            }

            byte[] acknowledgementMessageBytes =
                    "ack".getBytes(StandardCharsets.UTF_8);

            DatagramPacket outgoingAcknowledgementPacket =
                    new DatagramPacket(
                            acknowledgementMessageBytes,
                            acknowledgementMessageBytes.length,
                            serverIpAddress,
                            SERVER_PORT_NUMBER
                    );

            clientUdpSocket.send(
                    outgoingAcknowledgementPacket
            );

            System.out.println("ack sent");

        } catch (SocketTimeoutException exception) {

            System.out.println(
                    "No response from server within "
                            + MAX_RECEIVE_WAIT_TIME_IN_MILLISECONDS
                            + " ms"
            );

        } catch (IOException | InterruptedException exception) {

            throw new RuntimeException(
                    "Client error",
                    exception
            );
        }
    }
}
