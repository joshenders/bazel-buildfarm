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

abstract class PipelineStage implements Runnable {
  protected final Worker worker;
  protected final PipelineStage output;
  private final PipelineStage error;

  private PipelineStage input;
  protected boolean claimed;
  private boolean closed;

  PipelineStage(Worker worker, PipelineStage output, PipelineStage error) {
    this.worker = worker;
    this.output = output;
    this.error = error;

    input = null;
    claimed = false;
    closed = false;
  }

  public void setInput(PipelineStage input) {
    this.input = input;
  }

  @Override
  public void run() {
    try {
      while (!output.isClosed() || isClaimed()) {
        iterate();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      close();
    }
  }

  protected void iterate() throws InterruptedException {
    OperationContext operationContext;
    try {
      operationContext = take();
      OperationContext nextOperationContext = tick(operationContext);
      if (nextOperationContext != null && output.claim()) {
        output.offer(nextOperationContext);
      } else {
        error.offer(operationContext);
      }
    } finally {
      release();
    }
    after(operationContext);
  }

  protected OperationContext tick(OperationContext operationContext) {
    return operationContext;
  }

  protected void after(OperationContext operationContext) { }

  public synchronized boolean claim() throws InterruptedException {
    while (!closed && claimed) {
      wait();
    }
    if (closed) {
      return false;
    }
    claimed = true;
    return true;
  }

  public synchronized void release() {
    claimed = false;
    notify();
  }

  public void close() {
    closed = true;
  }

  public boolean isClosed() {
    return closed;
  }

  protected boolean isClaimed() {
    return claimed;
  }

  public PipelineStage output() {
    return this.output;
  }

  public PipelineStage error() {
    return this.error;
  }

  abstract OperationContext take() throws InterruptedException;
  abstract void offer(OperationContext operationContext);
}
