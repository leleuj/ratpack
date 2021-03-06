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

package ratpack.test.remote;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import io.remotecontrol.CommandChain;
import io.remotecontrol.client.CommandGenerator;
import io.remotecontrol.client.RemoteControlSupport;
import io.remotecontrol.client.UnserializableResultStrategy;
import io.remotecontrol.groovy.ClosureCommand;
import io.remotecontrol.groovy.client.ClosureCommandGenerator;
import io.remotecontrol.groovy.client.RawClosureCommand;
import io.remotecontrol.transport.http.HttpTransport;
import ratpack.remote.CommandDelegate;
import ratpack.test.ApplicationUnderTest;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static ratpack.remote.RemoteControlModule.DEFAULT_REMOTE_CONTROL_PATH;

public class RemoteControl {

  private final RemoteControlSupport<ClosureCommand> support;
  private final CommandGenerator<RawClosureCommand, ClosureCommand> generator = new ClosureCommandGenerator();

  public RemoteControl(ApplicationUnderTest application, String path) {
    support = new RemoteControlSupport<>(new HttpTransport(application.getAddress() + path), UnserializableResultStrategy.THROW, getClass().getClassLoader());
  }

  public RemoteControl(ApplicationUnderTest application) {
    this(application, DEFAULT_REMOTE_CONTROL_PATH);
  }

  public Object exec(@DelegatesTo(value = CommandDelegate.class, strategy = Closure.DELEGATE_FIRST) Closure<?>... commands) throws IOException {
    List<ClosureCommand> closureCommands = new LinkedList<>();
    for (Closure<?> command : commands) {
      ClosureCommand closureCommand = generator.generate(new RawClosureCommand(command, Collections.<Closure<?>>emptyList()));
      closureCommands.add(closureCommand);
    }

    return support.send(CommandChain.of(ClosureCommand.class, closureCommands));
  }

  public static Closure<?> command(@DelegatesTo(value = CommandDelegate.class, strategy = Closure.DELEGATE_FIRST) Closure<?> command) {
    return command.dehydrate();
  }


}
