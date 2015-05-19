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

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.wire.TextWire;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.Wires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StreamCorruptedException;

/**
 * Created by peter.lawrey on 22/01/15.
 */
public abstract class WireTcpHandler implements TcpHandler {
    public static final int SIZE_OF_SIZE = 4;
    protected Wire inWire, outWire;

    private static final Logger LOG = LoggerFactory.getLogger(WireTcpHandler.class);

    @Override
    public void process(Bytes in, Bytes out) {
        checkWires(in, out);

        if (in.remaining() < SIZE_OF_SIZE) {
            long outPos = out.position();

            publish(outWire);

            long written = out.position() - outPos;
            if (written == 0) {
                out.position(outPos);
                return;
            }
            assert written <= 1 << TcpEventHandler.CAPACITY;

            return;
        }


        do {

            if (!read(in, out))
                return;

        } while (in.remaining() > SIZE_OF_SIZE && out.remaining() > out.capacity() / SIZE_OF_SIZE);
    }


    /**
     * process all messages in this batch, provided there is plenty of output space.
     *
     * @param in  the source bytes
     * @param out the destination bytes
     * @return true if we can read attempt the next
     */
    private boolean read(Bytes in, Bytes out) {

        long length = Wires.lengthOf(in.readInt(in.position()));

        assert length >= 0 && length < 1 << 22 : "in=" + in + ", hex=" + in.toHexString();

        if (length == 0) {
            in.skip(SIZE_OF_SIZE);
            return false;
        }

        if (in.remaining() < length)   {
            // we have to first read more data before this can be processed
            if (LOG.isDebugEnabled())
                LOG.debug("required length=" + length + " but only got " + in.remaining() + " bytes, " +
                        "this is " +
                        "short by " + (length - +in.remaining()) + " bytes");
            return false;
        }

        long limit = in.limit();
        long end = in.position() + length + SIZE_OF_SIZE;
        long outPos = out.position();
        try {

            in.limit(end);

            final long position = inWire.bytes().position();
            try {
                process(inWire, outWire);
            } finally {
                inWire.bytes().position(position + length);
            }

            long written = out.position() - outPos;

            if (written > 0)
                return false;

        } catch (Exception e) {
            LOG.error("", e);
        } finally {
            in.limit(limit);
            in.position(end);
        }


        return true;
    }

    private boolean recreateWire;

    protected void recreateWire(boolean recreateWire) {
        this.recreateWire = recreateWire;
    }

    private void checkWires(Bytes in, Bytes out) {

        if (recreateWire) {
            recreateWire = false;
            inWire = createWriteFor(in);
            outWire = createWriteFor(out);
            return;
        }

        if ((inWire == null || inWire.bytes() != in)) {
            inWire = createWriteFor(in);
            recreateWire = false;
        }

        if ((outWire == null || outWire.bytes() != out)) {
            outWire = createWriteFor(out);
            recreateWire = false;
        }
    }

    protected Wire createWriteFor(Bytes bytes) {
        return new TextWire(bytes);
    }

    /**
     * Process an incoming request
     */

    /**
     * @param in  the wire to be processed
     * @param out the result of processing the {@code in}
     * @throws StreamCorruptedException if the wire is corrupt
     */
    protected abstract void process(Wire in, Wire out) throws StreamCorruptedException;

    /**
     * Publish some data
     */
    protected void publish(Wire out) {
    }
}