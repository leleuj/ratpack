/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.handling.internal;

import ratpack.api.Nullable;
import ratpack.handling.Chain;
import ratpack.handling.Handler;
import ratpack.launch.LaunchConfig;
import ratpack.registry.Registry;
import ratpack.func.Transformer;

import java.util.List;

public class ChainActionTransformer implements Transformer<List<Handler>, Chain> {

  private final LaunchConfig launchConfig;
  private final Registry registry;

  public ChainActionTransformer(LaunchConfig launchConfig, @Nullable Registry registry) {
    this.launchConfig = launchConfig;
    this.registry = registry;
  }

  public Chain transform(List<Handler> handlers) {
    return new DefaultChain(handlers, launchConfig, registry);
  }

}
