/**
 * Copyright (c) 2012, Oliver Kleine, Institute of Telematics, University of Luebeck
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source messageCode must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uzl.itm.ncoap.communication.reliability.outbound;

import de.uzl.itm.ncoap.message.CoapMessage;
import de.uzl.itm.ncoap.message.CoapResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by olli on 26.09.14.
 */
public class OutboundReliableMessageTransfer extends OutboundMessageTransfer {

    private static Logger LOG = LoggerFactory.getLogger(OutboundReliableMessageTransfer.class.getName());

    public static final int MAX_RETRANSMISSIONS = 4;

    /**
     * The minimum number of milliseconds (2000) to wait for the first retransmit of an outgoing
     * {@link de.uzl.itm.ncoap.message.CoapMessage} with
     * {@link de.uzl.itm.ncoap.message.MessageType.Name#CON}
     */
    public static final int ACK_TIMEOUT_MILLIS = 2000;

    /**
     * The factor (1.5) to be multiplied with {@link #ACK_TIMEOUT_MILLIS} to get the maximum number of milliseconds
     * (3000) to wait for the first retransmit of an outgoing {@link de.uzl.itm.ncoap.message.CoapMessage} with
     * {@link de.uzl.itm.ncoap.message.MessageType.Name#CON}
     */
    public static final double ACK_RANDOM_FACTOR = 1.5;

    private ScheduledFuture retransmissionFuture;
    private CoapMessage coapMessage;
    private int retransmissions;
    private boolean confirmed;

    private static final Random RANDOM = new Random(System.currentTimeMillis());

    /**
     * Provides a random(!) delay for the given retransmission number according to the CoAP specification
     * @param retransmission the retransmission number (e.g. 2 for the 2nd retransmission)
     * @return a random(!) delay for the given retransmission number according to the CoAP specification
     */
    public static long provideRetransmissionDelay(int retransmission){
        return (long)(Math.pow(2, retransmission - 1) * ACK_TIMEOUT_MILLIS *
                (1 + RANDOM.nextDouble() * (ACK_RANDOM_FACTOR - 1)));
    }

    /**
     * Creates a new instance of
     * {@link OutboundReliableMessageTransfer}
     *
     * @param remoteEndpoint the intended recipient of the {@link de.uzl.itm.ncoap.message.CoapMessage}
     * @param coapMessage the {@link de.uzl.itm.ncoap.message.CoapMessage} to be reliably transfered
     */
    public OutboundReliableMessageTransfer(InetSocketAddress remoteEndpoint, CoapMessage coapMessage) {
        super(remoteEndpoint, coapMessage.getMessageID(), coapMessage.getToken());
        this.coapMessage = coapMessage;
        this.retransmissions = 0;
        this.confirmed = false;
    }


//    @Override
//    public void updateRemoteSocket(InetSocketAddress remoteSocket){
//        super.updateRemoteSocket(remoteSocket);
//    }


    /**
     * Increases the number of retransmissions by one and returns the the actual number of retransmissions
     * @return the actual number of retransmissions (after increasing)
     */
    public int increaseRetransmissions(){
        return ++this.retransmissions;
    }

    /**
     * Returns the delay for the next retransmission
     * @return the delay for the next retransmission
     */
    public long getNextRetransmissionDelay(){
        return provideRetransmissionDelay(retransmissions + 1);
    }

    /**
     * Sets the {@link java.util.concurrent.ScheduledFuture} of the next retransmission
     * @param retransmissionFuture the {@link java.util.concurrent.ScheduledFuture} of the next retransmission
     */
    public void setRetransmissionFuture(ScheduledFuture retransmissionFuture){
        this.retransmissionFuture = retransmissionFuture;
    }

    /**
     * Sets the {@link de.uzl.itm.ncoap.message.CoapResponse} to be sent with the next retransmission
     * @param coapResponse the {@link de.uzl.itm.ncoap.message.CoapResponse} to be sent with the next retransmission
     */
    public void updateCoapMessage(CoapResponse coapResponse){
        this.coapMessage = coapResponse;
        LOG.info("Updated CoAP message for retransmission #{}: {}", (this.retransmissions + 1), coapResponse);
    }

    public CoapMessage getCoapMessage(){
        return this.coapMessage;
    }

    /**
     * Set this message exchange to be confirmed, i.e. stop further retransmissions.
     */
    public void setConfirmed(){
        if(this.retransmissionFuture.cancel(true)){
            LOG.info("Retransmission stopped (remote endpoint: {}, message ID: {})", this.getRemoteEndpoint(),
                    this.getMessageID());
        } else{
            LOG.error("Could not stop retransmission (remote endpoint: {}, message ID: {})",
                getRemoteEndpoint(), this.getMessageID()
            );
        }

        this.confirmed = true;
    }

    public boolean isConfirmed(){
        return this.confirmed;
    }
}