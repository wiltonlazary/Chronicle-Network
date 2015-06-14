/*
 * Copyright 2015 Higher Frequency Trading
 *
 * http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.network;

import net.openhft.chronicle.engine.api.SessionDetailsProvider;
import net.openhft.chronicle.network.event.EventHandler;
import net.openhft.chronicle.network.event.EventLoop;
import net.openhft.chronicle.network.event.HandlerPriority;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Supplier;

/**
 * Created by peter.lawrey on 22/01/15.
 */
public class AcceptorEventHandler implements EventHandler,Closeable {
    @NotNull
    private final Supplier<TcpHandler> tcpHandlerSupplier;
    @NotNull
    private final Supplier<SessionDetailsProvider> sessionDetailsSupplier;
    private EventLoop eventLoop;
    private final ServerSocketChannel ssc;

    private static final Logger LOG = LoggerFactory.getLogger(AcceptorEventHandler.class);

    public AcceptorEventHandler(int port,
                                @NotNull final Supplier<TcpHandler> tcpHandlerSupplier,
                                @NotNull final Supplier<SessionDetailsProvider> sessionDetailsSupplier) throws
            IOException {
        this.tcpHandlerSupplier = tcpHandlerSupplier;
        ssc = ServerSocketChannel.open();
        ssc.socket().setReuseAddress(true);
        ssc.bind(new InetSocketAddress(port));
        this.sessionDetailsSupplier = sessionDetailsSupplier;
    }

    public int getLocalPort() throws IOException {
        return ssc.socket().getLocalPort();
    }

    @Override
    public void eventLoop(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public boolean runOnce()  {
        try {

            SocketChannel sc = ssc.accept();

            if (sc != null) {

                final SessionDetailsProvider sessionDetails = sessionDetailsSupplier.get();

                sessionDetails.setClientAddress((InetSocketAddress) sc.getRemoteAddress());

                eventLoop.addHandler(new TcpEventHandler(sc,
                        tcpHandlerSupplier.get(),
                        sessionDetails));
            }

        } catch (AsynchronousCloseException e) {
            closeSocket();
        } catch (Exception e) {
            LOG.error("", e);
            closeSocket();
        }
        return false;
    }

    private void closeSocket() {
        try {
            ssc.socket().close();
        } catch (IOException ignored) {
        }

        try {
            ssc.close();
        } catch (IOException ignored) {
        }
    }

    @NotNull
    @Override
    public HandlerPriority priority() {
        return HandlerPriority.BLOCKING;
    }

    @Override
    public boolean isDead() {
        return !ssc.isOpen();
    }

    @Override
    public void close() throws IOException {
        closeSocket();
    }
}