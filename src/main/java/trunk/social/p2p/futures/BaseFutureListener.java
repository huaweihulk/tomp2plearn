/*
 * Copyright 2009 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package trunk.social.p2p.futures;

/**
 * Something interested in being notified when the result of an
 * {@link BaseFuture} becomes available.
 * 
 * @author Thomas Bocek
 * @param <F>
 */
public interface BaseFutureListener<F extends BaseFuture> {
    /**
     * Invoked when the operation associated with the {@link BaseFuture} has
     * been completed. If an operation already completed, then this method is not
     * invoked.
     * 
     * @param future
     *            The future operation
     */
    public abstract void operationComplete(F future) throws Exception;

    /**
     * If the #operationComplete() is called and the method throws an exception.
     * 
     * @param t
     *            The exception thrown in #operationComplete(BaseFuture).
     * @throws Exception
     *             If an exception is thrown, it is printed in the log and and
     *             System.err
     */
    public abstract void exceptionCaught(Throwable t) throws Exception;
}
