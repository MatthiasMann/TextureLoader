/*
 * Copyright (c) 2008-2013, Matthias Mann
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

import de.matthiasmann.textureloader.Texture.MipMapMode;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A texture manager which will load texture asynchronous on damand and also
 * unloads them when not used for a certain amount of time.
 * 
 * <p>To use this class with OpenGL core profile you need to enable core
 * profile mode before creating the TextureManager.</p>
 * 
 * <p>Example main loop:</p><pre>// before main loop
Texture.setUseCoreProfile(true/false); // this depends on your selected OpenGL profile
AsyncExecution async = new AsyncExecution();
TextureManager tm = new TextureManager(async);
...
// main loop
while(!Display.isCloseRequested()) {
   GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
   
   async.executeQueuedJobs();
   tm.nextFrame();
   
   handleInput();
   renderStuff();
   
   Display.update();
}</pre>
 * @author Matthias Mann
 * @see Texture#setUseCoreProfile(boolean) 
 */
public class TextureManager {

    static final int SWEEP_TIMEOUT = 60 * 10;  // 10sec @ 60Hz
    static final int TEXTURES_TO_SWEEP_PER_FRAME = 10;
    
    static final Logger LOGGER = Logger.getLogger(TextureManager.class.getName());

    final AsyncExecution asyncExecution;
    final ExecutorService executor;
    
    final Texture dummyTexture;
    final HashMap<String, Texture> textures;
    final ArrayList<Texture> activeTextures;
    final Texture.MipMapMode mipMapMode;

    long currentFrame;
    int sweepTimeout;
    int sweepIndex;

    /**
     * Creates a texture manager which uses a single threaded {@link ExecutorService} to
     * perform async loading of textures.
     * <p>It is important to call {@link AsyncExecution#executeQueuedJobs() } on the
     * main thread at least once per frame.</p>
     * 
     * @param asyncExecution the {@link AsyncExecution} instance used to bridge threads
     * @throws NullPointerException when asyncExecution is null
     */
    public TextureManager(AsyncExecution asyncExecution) {
        this(asyncExecution, Executors.newSingleThreadExecutor());
    }

    /**
     * Creates a texture manager which uses the specified {@link ExecutorService} to
     * perform async loading of textures.
     * <p>It is important to call {@link AsyncExecution#executeQueuedJobs() } on the
     * main thread at least once per frame.</p>
     * 
     * @param asyncExecution the {@link AsyncExecution} instance used to bridge threads
     * @param executor the ExecutorService used to load textures asynchronous to the GL thread
     * @throws NullPointerException when one of the arguments is null
     */
    public TextureManager(AsyncExecution asyncExecution, ExecutorService executor) {
        if(asyncExecution == null) {
            throw new NullPointerException("asyncExecution");
        }
        if(executor == null) {
            throw new NullPointerException("executor");
        }
        
        this.asyncExecution = asyncExecution;
        this.executor = executor;
        
        this.dummyTexture = new Texture(1, 1, Texture.Format.RGBA);
        this.textures = new HashMap<String, Texture>();
        this.activeTextures = new ArrayList<Texture>();
        this.mipMapMode = Texture.decideMipMapMode();

        // create a 1x1 fully transparent texture - byte order doesn't matter for 0
        ByteBuffer bb = ByteBuffer.allocateDirect(4);
        bb.putInt(0).flip();
        dummyTexture.upload(0, 0, 1, 1, bb);
    }

    /**
     * Creates a managed texture instance for the specified URL.
     * <p>Textures are cached using their {@link URL#toString() } as a key.</p>
     * <p>The texture is not loaded until it is first used.</p>
     * <p>The mipMapMode is dertermined based on {@link Texture#decideMipMapMode() }</p>
     * 
     * @param url the URL to load
     * @return the managed texture object
     */
    public Texture getTexture(URL url) {
        return getTexture(url, mipMapMode);
    }
    /**
     * Creates a managed texture instance for the specified URL.
     * <p>Textures are cached using their {@link URL#toString() } as a key.</p>
     * <p>The texture is not loaded until it is first used.</p>
     * <p>The mipMapMode can only be specified the first time the texture is requested.</p>
     * 
     * @param url the URL to load
     * @param mipMapMode the mipMapMode which should be used with this texture
     * @return the managed texture object
     */
    public synchronized Texture getTexture(URL url, MipMapMode mipMapMode) {
        String key = url.toString();
        Texture tex = textures.get(key);
        if(tex == null) {
            tex = new Texture(this, url, mipMapMode);
            textures.put(key, tex);
        }
        return tex;
    }

    /**
     * Checks for textures which have not been bound for a certain amount of time
     * and unloads them. Only a limited number of textures are unloaded per frame.
     */
    public synchronized void nextFrame() {
        currentFrame++;

        int idx = sweepIndex;
        if(idx <= 0) {
            idx = activeTextures.size();
        }

        for(int i=0 ; i<TEXTURES_TO_SWEEP_PER_FRAME && idx > 0 ; i++) {
            Texture t = activeTextures.get(--idx);
            assert t.id != 0 && t.id != dummyTexture.id : "Invalid texture in active list";
            if(currentFrame - t.lastUsedFrame > SWEEP_TIMEOUT) {
                unloadTexture(idx, t);
            }
        }
        sweepIndex = idx;
    }

    private void unloadTexture(int idx, Texture t) {
        LOGGER.log(Level.INFO, "Unloading texture: {0}", t.url);
        
        t.destroy();
        int end = activeTextures.size() - 1;
        t = activeTextures.remove(end);
        if(idx < end) {
            activeTextures.set(idx, t);
        }
    }
    
    synchronized void loadTexture(Texture texture) {
        URL url = texture.url;

        texture.id = dummyTexture.id;
        texture.width = 2;
        texture.height = 2;

        TextureLoader textureLoader = createTextureLoader(url);
        if(textureLoader == null) {
            LOGGER.log(Level.SEVERE, "Unknown file extension: {0}", url);
            return;
        }

        LOGGER.log(Level.INFO, "Loading texture: {0}", url);
        
        AsyncOpen open = new AsyncOpen(texture, textureLoader);
        invoke(open, open);
    }

    /**
     * Constructs a {@link TextureLoader} for the given URL.
     * <p>The file extension is used to select which subclass to use.</p>
     * <p>The following file extensions are supported: {@code PNG, JPG, TGA and BMP }</p>
     * @param url
     * @return 
     */
    public static TextureLoader createTextureLoader(URL url) {
        String path = url.getPath();
        if(endsWithIgnoreCase(path, ".png")) {
            return new TextureLoaderPNG(url);
        } else if(endsWithIgnoreCase(path, ".jpg")) {
            return new TextureLoaderJPEG(url);
        } else if(endsWithIgnoreCase(path, ".tga")) {
            return new TextureLoaderTGA(url);
        } else if(endsWithIgnoreCase(path, ".bmp")) {
            return new TextureLoaderBMP(url);
        } else {
            return null;
        }
    }

    synchronized<T> void invoke(Callable<T> c, AsyncCompletionListener<T> acl) {
        asyncExecution.invokeAsync(executor, c, acl);
    }

    static boolean endsWithIgnoreCase(String str, String end) {
        int len = end.length();
        return str.regionMatches(true, str.length() - len, end, 0, len);
    }
    
    class AsyncOpen implements Callable<TextureLoader>, AsyncCompletionListener<TextureLoader> {
        final Texture texture;
        final TextureLoader loader;

        public AsyncOpen(Texture texture, TextureLoader loader) {
            this.texture = texture;
            this.loader = loader;
        }

        @Override
        public TextureLoader call() throws Exception {
            if(loader.open()) {
                return loader;
            } else {
                return null;
            }
        }

        @Override
        public void completed(TextureLoader result) {
            if(result != null) {
                AsyncDecode decode = new AsyncDecode(texture, loader);
                invoke(decode, decode);
            } else {
                dispose();
            }
        }

        @Override
        public void failed(Exception ex) {
            LOGGER.log(Level.SEVERE, "Can't open texture: " + loader.url, ex);
            dispose();
        }

        void dispose() {
            loader.close();
        }
    }

    class AsyncDecode implements Callable<TextureLoader>, AsyncCompletionListener<TextureLoader> {
        final Texture texture;
        final TextureLoader textureLoader;
        final TextureBuffer textureBuffer;
        final ByteBuffer byteBuffer;

        public AsyncDecode(Texture texture, TextureLoader textureLoader) {
            this.texture = texture;
            this.textureLoader = textureLoader;
            this.textureBuffer = TextureBuffer.create(
                    textureLoader.getWidth() * textureLoader.getHeight() *
                    textureLoader.getFormat().getBytesPerPixel());
            this.byteBuffer = textureBuffer.map();
        }

        @Override
        public TextureLoader call() throws Exception {
            byteBuffer.clear();
            textureLoader.decode(byteBuffer);
            byteBuffer.flip();
            return textureLoader;
        }

        @Override
        public void completed(TextureLoader result) {
            textureBuffer.unmap();
            texture.width = result.getWidth();
            texture.height = result.getHeight();
            texture.format = result.getFormat();
            texture.createTexture();
            texture.uploadInt(0, 0, result.width, result.height, textureBuffer);
            activeTextures.add(texture);
            dispose();
        }

        @Override
        public void failed(Exception ex) {
            LOGGER.log(Level.SEVERE, "Can't decode texture", ex);
            dispose();
        }

        private void dispose() {
            textureBuffer.dispose();
            textureLoader.close();
        }
    }

}
