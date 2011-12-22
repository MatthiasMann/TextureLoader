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

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * A texture class. All methods need to be called from the GL thread.
 * 
 * @author Matthias Mann
 */
public final class Texture {

    public enum Format {
        RGBA(GL11.GL_RGBA, 4),
        BGRA(GL12.GL_BGRA, 4),
        LUMINANCE(GL11.GL_LUMINANCE, 1);

        final int glFormat;
        final int bpp;

        private Format(int glFormat, int bpp) {
            this.glFormat = glFormat;
            this.bpp = bpp;
        }

        public int getGLFormat() {
            return glFormat;
        }

        public int getBytesPerPixel() {
            return bpp;
        }
    }

    final TextureManager manager;
    final URL url;
    int id;
    int width;
    int height;
    Format format;
    long lastUsedFrame;

    Texture(TextureManager manager, URL url) {
        this.manager = manager;
        this.url = url;
    }

    /**
     * Creates an unmanaged texture
     *
     * @param width the width
     * @param height the height
     * @param format the format of the image data
     */
    public Texture(int width, int height, Format format) {
        if(format == null) {
            throw new NullPointerException("format");
        }
        this.manager = null;
        this.url = null;
        this.width = width;
        this.height = height;
        this.format = format;

        createTexture();
    }

    /**
     * Loads an unmanaged texture synchronously
     * @param url the URL to load
     * @return the Texture object or null if the URL is not a supported image file
     * @throws IOException if an IO or decode error happend
     */
    public static Texture loadTexture(URL url) throws IOException {
        TextureLoader textureLoader = TextureManager.createTextureLoader(url);
        if(textureLoader == null) {
            return null;
        }
        if(!textureLoader.open()) {
            return null;
        }
        
        int width = textureLoader.getWidth();
        int height = textureLoader.getHeight();
        
        TextureBuffer textureBuffer = TextureBuffer.create(
            width * height *
            textureLoader.getFormat().getBytesPerPixel());
        try {
            ByteBuffer byteBuffer = textureBuffer.map();
            try {
                textureLoader.decode(byteBuffer);
                Texture texture = new Texture(width, height,
                        textureLoader.getFormat());
                texture.upload(0, 0, width, height, textureBuffer);
                return texture;
            } finally {
                textureBuffer.unmap();
            }
        } finally {
            textureBuffer.dispose();
        }
    }
    
    /**
     * Returns true if this texture object was created via {@link TextureManager#getTexture(java.net.URL) }
     * @return true if this texture object is managed
     */
    public boolean isManaged() {
        return manager != null;
    }

    /**
     * Retrieves the width of an unmanaged texture.
     * @return the width for an unmanaged texture and -1 for a managed texture.
     */
    public int getWidth() {
        return isManaged() ? -1 : width;
    }

    /**
     * Retrieves the height of an unmanaged texture.
     * @return the height for an unmanaged texture and -1 for a managed texture.
     */
    public int getHeight() {
        return isManaged() ? -1 : height;
    }

    /**
     * Returns the format used to upload texture data of an unmanaged texture.
     * @return the format for uploading texture data and null for a managed texture.
     */
    public Format getFormat() {
        return isManaged() ? null : format;
    }
    
    /**
     * Uploads texture data
     * 
     * @param x the horizontal start of the area to modify
     * @param y the vertical start of the area to modify
     * @param width the width of the area to modify
     * @param height the height of the area to modify
     * @param bb the direct ByteBuffer containing the texture data - interpreted as unsigned bytes
     */
    public void upload(int x, int y, int width, int height, ByteBuffer bb) {
        checkNotManaged();
        bind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, format.glFormat, GL11.GL_UNSIGNED_BYTE, bb);
    }

    /**
     * Uploads texture data
     * 
     * @param x the horizontal start of the area to modify
     * @param y the vertical start of the area to modify
     * @param width the width of the area to modify
     * @param height the height of the area to modify
     * @param ib the direct IntBuffer containing the texture data - interpreted as unsigned bytes
     */
    public void upload(int x, int y, int width, int height, IntBuffer ib) {
        checkNotManaged();
        bind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, format.glFormat, GL11.GL_UNSIGNED_BYTE, ib);
    }

    /**
     * Uploads texture data
     * 
     * @param x the horizontal start of the area to modify
     * @param y the vertical start of the area to modify
     * @param width the width of the area to modify
     * @param height the height of the area to modify
     * @param buf the TextureBuffer containing the texture data - interpreted as unsigned bytes
     */
    public void upload(int x, int y, int width, int height, TextureBuffer buf) {
        checkNotManaged();
        uploadInt(x, y, width, height, buf);
    }
    
    private void checkNotManaged() {
        if(isManaged()) {
            throw new UnsupportedOperationException("Can't modify managed textures");
        }
    }
    
    void uploadInt(int x, int y, int width, int height, TextureBuffer buf) {
        bind();
        if(buf instanceof TextureBuffer.TextureBufferPBO) {
            TextureBuffer.TextureBufferPBO pbo = (TextureBuffer.TextureBufferPBO)buf;
            assert !pbo.isMapped();
            pbo.bind();
            try {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, format.glFormat, GL11.GL_UNSIGNED_BYTE, 0);
            } finally {
                pbo.unbind();
            }
        } else if(buf instanceof TextureBuffer.TextureBufferPool) {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, format.glFormat, GL11.GL_UNSIGNED_BYTE, buf.map());
        } else {
            throw new IllegalArgumentException("Unsupported TextureBuffer type");
        }
    }

    /**
     * Binds the texture for rendering. If the texture is managed and not yet loaded
     * then it's loading will be started asynchronously.
     */
    public void bind() {
        if(manager != null) {
            lastUsedFrame = manager.currentFrame;
            if(id == 0) {
                manager.loadTexture(this);
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    void createTexture() {
        id = GL11.glGenTextures();
        bind();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, format.glFormat, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
    }

    void destroy() {
        GL11.glDeleteTextures(id);
        id = 0;
        width = 0;
        height = 0;
    }
}
