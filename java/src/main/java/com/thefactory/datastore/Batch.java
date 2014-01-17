package com.thefactory.datastore;

import java.io.DataOutput;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Iterator;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;

public class Batch {
    private final ChannelBuffer buffer;
    private final DataOutput out;
    
    public Batch() {
        buffer = ChannelBuffers.dynamicBuffer();
        out = new ChannelBufferOutputStream(buffer);
    }

    public Batch(Slice fromSlice) {
        buffer = ChannelBuffers.wrappedBuffer(fromSlice.toArray());
        out = new ChannelBufferOutputStream(buffer);
    }

    public void put(Slice key, Slice val) {
        if(!buffer.writable()){
            throw new UnsupportedOperationException("Batch with fixed size cannot grow");
        }
        try {
            writeSlice(key);
            writeSlice(val);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } 
    }

    public void delete(Slice key) {
        if(!buffer.writable()){
            throw new UnsupportedOperationException("Batch with fixed size cannot grow");
        }
        try {
            writeSlice(key);
            out.writeByte(Msgpack.NIL_VALUE);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } 
     }

    public void clear() {
        buffer.clear();
    }

    public boolean isEmpty() {
        return bytesLeft() == 0;
    }

    public Slice asSlice(){
        return new Slice(buffer.array(), buffer.readerIndex(), bytesLeft());
    }

    private void writeSlice(Slice slice) throws IOException {
        Msgpack.writeRawLength(out, slice.getLength());
        out.write(slice.toArray(), slice.getOffset(), slice.getLength());
    }

    private int bytesLeft() {
        return buffer.writerIndex() - buffer.readerIndex();
    }

    private int readLength() throws IOException {
        Slice in = asSlice();
        int offsetBefore = in.getOffset();
        int ret = Msgpack.readRawLength(in);
        int nBytesRead = in.getOffset() - offsetBefore;
        buffer.skipBytes(nBytesRead);
        return ret;
    }

    private Slice readSlice() throws IOException {
        if(buffer.getUnsignedByte(buffer.readerIndex()) == Msgpack.NIL_VALUE) {
            buffer.skipBytes(1);
            return null;
        } 
        int len = readLength();
        Slice ret = new Slice(buffer.array(), buffer.readerIndex(), len);
        buffer.skipBytes(len);
        return ret;
    }

    private KV readOne() throws IOException {
        Slice key = readSlice();
        Slice value = readSlice();
        KV ret = new KV(key, value);
        if(value == null) {
            ret.tombstone(key);
        }  
        return ret;
    }

    public Iterator<KV> pairs() {
        return new Iterator<KV>() {
            public boolean hasNext() {
                return bytesLeft() > 0;
            }

            public KV next() {
                try {
                    return readOne();
                } catch (IOException e) {
                    throw new NoSuchElementException(e.getMessage());
                }
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}

