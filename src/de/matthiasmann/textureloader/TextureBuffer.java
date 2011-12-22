/*
 * Copyright (c) 2008-2011, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.textureloader;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBBufferObject;
import org.lwjgl.opengl.ARBMapBufferRange;
import org.lwjgl.opengl.ARBPixelBufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

/**
 * A buffer for texture data. If supported it is backed by an OpenGL PBO.
 * 
 * @see ARBPixelBufferObject
 * @author Matthias Mann
 */
public abstract class TextureBuffer {

    public static boolean USE_PBO = true;

    public static TextureBuffer create(int size) {
        if(USE_PBO) {
            ContextCapabilities caps = GLContext.getCapabilities();
            if(caps.GL_ARB_pixel_buffer_object) {
                if(caps.GL_ARB_map_buffer_range) {
                    return new TextureBufferPBO_MBR(size);
                }
                return new TextureBufferPBO(size);
            }
        }
        return new TextureBufferPool(size);
    }

    /**
     * Maps the buffer. After mapping no GL operation may be executed with this
     * Buffer. {@link #unmap()} must be called after map in any case.
     * The content of the buffer will be undefined after mapping it.
     *
     * @return the mapped buffer
     */
    public abstract ByteBuffer map();

    /**
     * Unmap the mapping. After unmapping the {@code ByteBuffer} returned by
     * {@link #map()} must not be accessed.
     */
    public abstract void unmap();

    /**
     * Dispose this Buffer.
     * After dispose this Buffer may not be used in any way.
     */
    public abstract void dispose();

    static class TextureBufferPBO extends TextureBuffer {
        final int size;
        int id;
        ByteBuffer bb;
        boolean mapped;

        TextureBufferPBO(int size) {
            this.size = size;
            id = ARBBufferObject.glGenBuffersARB();
            
            bind();
            ARBBufferObject.glBufferDataARB(
                    ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB, size,
                    ARBBufferObject.GL_DYNAMIC_DRAW_ARB);
            unbind();
        }

        @Override
        public void dispose() {
            if(id != 0) {
                unmap();
                ARBBufferObject.glDeleteBuffersARB(id);
                id = 0;
            }
        }

        public boolean isMapped() {
            return mapped;
        }

        @Override
        public ByteBuffer map() {
            if(!mapped) {
                bind();
                doMap();
                mapped = true;
                unbind();
            }
            bb.order(ByteOrder.nativeOrder()).clear();
            return bb;
        }

        @Override
        public void unmap() {
            if(mapped) {
                bind();
                ARBBufferObject.glUnmapBufferARB(ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB);
                unbind();
                mapped = false;
            }
        }
        
        protected void doMap() {
            bb = ARBBufferObject.glMapBufferARB(
                    ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB,
                    ARBBufferObject.GL_WRITE_ONLY_ARB, size, bb);
        }

        final void bind() {
            if(id == 0) {
                throw new IllegalStateException("Already disposed");
            }
            ARBBufferObject.glBindBufferARB(ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB, id);
        }

        final void unbind() {
            ARBBufferObject.glBindBufferARB(ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB, 0);
        }
    }
    
    static class TextureBufferPBO_MBR extends TextureBufferPBO {
        TextureBufferPBO_MBR(int size) {
            super(size);
        }

        @Override
        protected void doMap() {
            bb = ARBMapBufferRange.glMapBufferRange(
                    ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB,
                    0, size,
                    ARBMapBufferRange.GL_MAP_INVALIDATE_BUFFER_BIT |
                    ARBMapBufferRange.GL_MAP_WRITE_BIT, bb);
        }
    }

    static final class TextureBufferPool extends TextureBuffer {
        private static final int ROUND_SIZE = 8192;
        private static final ThreadLocal<SoftReference<ByteBuffer>> pool = new ThreadLocal<SoftReference<ByteBuffer>>();

        ByteBuffer bb;

        TextureBufferPool(int size) {
            bb = getPoolBuffer();
            if(bb == null || bb.capacity() < size) {
                bb = BufferUtils.createByteBuffer((size + ROUND_SIZE - 1) & ~ROUND_SIZE);
            } else {
                pool.set(null);
            }
        }

        @Override
        public void dispose() {
            if(bb != null) {
                ByteBuffer poolBuf = getPoolBuffer();
                if(poolBuf == null || poolBuf.capacity() < bb.capacity()) {
                    pool.set(new SoftReference<ByteBuffer>(bb));
                }
                bb = null;
            }
        }

        @Override
        public ByteBuffer map() {
            bb.clear();
            return bb;
        }

        @Override
        public void unmap() {
        }

        private static ByteBuffer getPoolBuffer() {
            SoftReference<ByteBuffer> ref = pool.get();
            return (ref != null) ? ref.get() : null;
        }
    }
}
