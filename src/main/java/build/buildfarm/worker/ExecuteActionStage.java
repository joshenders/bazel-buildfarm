// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buildfarm.worker;

import com.google.common.collect.Iterables;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class ExecuteActionStage extends PipelineStage {
  private final Set<Thread> executors;
  private BlockingQueue<OperationContext> queue;

  ExecuteActionStage(Worker worker, PipelineStage output, PipelineStage error) {
    super(worker, output, error);
    queue = new ArrayBlockingQueue<>(1);
    executors = new HashSet<>();
  }

  @Override
  public void offer(OperationContext operationContext) {
    queue.offer(operationContext);
  }

  @Override
  public OperationContext take() throws InterruptedException {
    return queue.take();
  }

  @Override
  public synchronized boolean claim() throws InterruptedException {
    if (isClosed()) {
      return false;
    }

    while (executors.size() >= worker.config.getExecuteStageWidth()) {
      wait();
    }
    return true;
  }

  @Override
  public synchronized void release() {
    if (!executors.remove(Thread.currentThread())) {
      throw new IllegalStateException();
    }
    this.notify();
  }

  @Override
  protected boolean isClaimed() {
    return Iterables.any(executors, (executor) -> executor.isAlive());
  }

  @Override
  protected void iterate() throws InterruptedException {
    Thread executor = new Thread(new Executor(worker, take(), this));

    synchronized(this) {
      executors.add(executor);
    }

    executor.start();
  }
}
