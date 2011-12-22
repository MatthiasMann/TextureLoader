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

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Allows to execute code on a different thread and retrieve the
 * result synchronously by calling {@link #executeQueuedJobs() }
 * 
 * @author Matthias Mann
 */
public class AsyncExecution {
    
    private final LinkedBlockingQueue<Runnable> completionQueue;

    public AsyncExecution() {
        this.completionQueue = new LinkedBlockingQueue<Runnable>();
    }
    
    /**
     * Queues a job for execution by {@link #executeQueuedJobs() }
     * @param job the job
     * @throws NullPointerException when job is null
     */
    public void invokeLater(Runnable job) {
        if(job == null) {
            throw new NullPointerException("job");
        }
        completionQueue.add(job);
    }
    
    /**
     * Executes all queued jobs
     * @see #invokeLater(java.lang.Runnable) 
     */
    public void executeQueuedJobs() {
        Runnable job;
        while((job=completionQueue.poll()) != null) {
            try {
                job.run();
            } catch(Exception ex) {
                Logger.getLogger(AsyncExecution.class.getName()).log(Level.SEVERE,
                        "Exception while executing queued job", ex);
            }
        }
    }
    
    /**
     * Invokes a {@link Callable} job on the specified executor and invokes the
     * {@code listener} via {@link #invokeLater(java.lang.Runnable) } once
     * the job has completed normally or with an exception.
     * 
     * @param <V> the return type of the callable
     * @param executor the executor for the async execution of the job
     * @param asyncJob the job to execute
     * @param listener the listener to invoke once the job has completed
     * @throws NullPointerException when any of the arguments is null.
     */
    public<V> void invokeAsync(Executor executor, Callable<V> asyncJob, AsyncCompletionListener<V> listener) {
        if(executor == null) {
            throw new NullPointerException("executor");
        }
        if(asyncJob == null) {
            throw new NullPointerException("asyncJob");
        }
        if(listener == null) {
            throw new NullPointerException("listener");
        }
        executor.execute(new AC<V>(asyncJob, null, listener));
    }
    
    /**
     * Invokes a {@link Runnable} job on the specified executor and invokes the
     * {@code listener} via {@link #invokeLater(java.lang.Runnable) } once
     * the job has completed normally or with an exception.
     * 
     * @param executor the executor for the async execution of the job
     * @param asyncJob the job to execute
     * @param listener the listener to invoke once the job has completed
     * @throws NullPointerException when any of the arguments is null.
     */
    public void invokeAsync(Executor executor, Runnable asyncJob, AsyncCompletionListener<Void> listener) {
        if(executor == null) {
            throw new NullPointerException("executor");
        }
        if(asyncJob == null) {
            throw new NullPointerException("asyncJob");
        }
        if(listener == null) {
            throw new NullPointerException("listener");
        }
        executor.execute(new AC<Void>(null, asyncJob, listener));
    }
    
    class AC<V> implements Callable<V>, Runnable {
        private final Callable<V> jobC;
        private final Runnable jobR;
        private final AsyncCompletionListener<V> listener;
        private V result;
        private Exception exception;

        AC(Callable<V> jobC, Runnable jobR, AsyncCompletionListener<V> listener) {
            this.jobC = jobC;
            this.jobR = jobR;
            this.listener = listener;
        }

        @Override
        public V call() throws Exception {
            try {
                if(jobC != null) {
                    result = jobC.call();
                } else {
                    jobR.run();
                }
                invokeLater(this);
                return result;
            } catch(Exception ex) {
                exception = ex;
                invokeLater(this);
                throw ex;
            }
        }

        @Override
        public void run() {
            if(exception != null) {
                listener.failed(exception);
            } else {
                listener.completed(result);
            }
        }
    }
}
