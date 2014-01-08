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

package ratpack.codahale.metrics;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.CsvReporter;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import ratpack.codahale.metrics.internal.ConsoleReporterProvider;
import ratpack.codahale.metrics.internal.CsvReporterProvider;
import ratpack.codahale.metrics.internal.GaugeTypeListener;
import ratpack.codahale.metrics.internal.JmxReporterProvider;
import ratpack.codahale.metrics.internal.MeteredMethodInterceptor;
import ratpack.codahale.metrics.internal.RequestTimingHandler;
import ratpack.codahale.metrics.internal.TimedMethodInterceptor;
import ratpack.guice.HandlerDecoratingModule;
import ratpack.guice.internal.GuiceUtil;
import ratpack.handling.Handler;
import ratpack.util.Action;

import java.io.File;

/**
 * An extension module that provides support for Coda Hale's Metrics.
 * <p>
 * To use it one has to register the module and enable the required functionality by chaining the various configuration
 * options.  For example, to enable the capturing and reporting of metrics to {@link ratpack.codahale.metrics.CodaHaleMetricsModule#jmx()}
 * one would write: (Groovy DSL)
 * </p>
 * <pre class="groovy-ratpack-dsl">
 * import ratpack.codahale.metrics.CodaHaleMetricsModule
 * import static ratpack.groovy.Groovy.ratpack
 *
 * ratpack {
 *   modules {
 *     register new CodaHaleMetricsModule().jmx()
 *   }
 * }
 * </pre>
 * <p>
 * To enable the capturing and reporting of metrics to JMX and the {@link ratpack.codahale.metrics.CodaHaleMetricsModule#console()}, one would
 * write: (Groovy DSL)
 * </p>
 * <pre class="groovy-ratpack-dsl">
 * import ratpack.codahale.metrics.CodaHaleMetricsModule
 * import static ratpack.groovy.Groovy.ratpack
 *
 * ratpack {
 *   modules {
 *     register new CodaHaleMetricsModule().jmx().console()
 *   }
 * }
 * </pre>
 * <p>
 * This module supports both metric collection and health checks.  For further details on both please see
 * {@link ratpack.codahale.metrics.CodaHaleMetricsModule#metrics()} and {@link ratpack.codahale.metrics.CodaHaleMetricsModule#healthChecks()}
 * respectively.  By default metric collection is not enabled but health checks are.
 * </p>
 * <p>
 * <b>It is important that this module is registered first in the modules list to ensure that request metrics are as accurate as possible.</b>
 * </p>
 *
 * @see <a href="http://metrics.codahale.com/" target="_blank">Coda Hale's Metrics</a>
 */
public class CodaHaleMetricsModule extends AbstractModule implements HandlerDecoratingModule {

  private boolean reportMetricsToJmx;
  private boolean reportMetricsToConsole;
  private File csvReportDirectory;
  private boolean healthChecksEnabled = true;
  private boolean jvmMetricsEnabled;
  private boolean metricsEnabled;

  private boolean isMetricsEnabled() {
    return metricsEnabled || jvmMetricsEnabled || reportMetricsToConsole || reportMetricsToJmx || csvReportDirectory != null;
  }

  @Override
  protected void configure() {
    if (isMetricsEnabled()) {
      final MetricRegistry metricRegistry = new MetricRegistry();
      bind(MetricRegistry.class).toInstance(metricRegistry);

      MeteredMethodInterceptor meteredMethodInterceptor = new MeteredMethodInterceptor();
      requestInjection(meteredMethodInterceptor);
      bindInterceptor(Matchers.any(), Matchers.annotatedWith(Metered.class), meteredMethodInterceptor);

      TimedMethodInterceptor timedMethodInterceptor = new TimedMethodInterceptor();
      requestInjection(timedMethodInterceptor);
      bindInterceptor(Matchers.any(), Matchers.annotatedWith(Timed.class), timedMethodInterceptor);

      GaugeTypeListener gaugeTypeListener = new GaugeTypeListener(metricRegistry);
      bindListener(Matchers.any(), gaugeTypeListener);

      if (reportMetricsToJmx) {
        bind(JmxReporter.class).toProvider(JmxReporterProvider.class).asEagerSingleton();
      }

      if (reportMetricsToConsole) {
        bind(ConsoleReporter.class).toProvider(ConsoleReporterProvider.class).asEagerSingleton();
      }

      if (csvReportDirectory != null) {
        bind(File.class).annotatedWith(Names.named(CsvReporterProvider.CSV_REPORT_DIRECTORY)).toInstance(csvReportDirectory);
        bind(CsvReporter.class).toProvider(CsvReporterProvider.class).asEagerSingleton();
      }
    }

    if (healthChecksEnabled) {
      bind(HealthCheckRegistry.class).in(Singleton.class);
    }
  }

  /**
   * Enables the collection of metrics.
   * <p>
   * To enable one of the built in metric reporters please chain the relevant reporter configuration
   * e.g. {@link ratpack.codahale.metrics.CodaHaleMetricsModule#jmx()}, {@link ratpack.codahale.metrics.CodaHaleMetricsModule#console()}.
   * </p>
   * <p>
   * By default {@link com.codahale.metrics.Timer} metrics are collected for all requests received.  Please see
   * {@link RequestTimingHandler} for further details.
   * </p>
   * <p>
   * Additional custom metrics can be registered with the provided {@link MetricRegistry} instance
   * </p>
   * <p>
   * Example custom metrics: (Groovy DSL)
   * </p>
   * <pre class="groovy-ratpack-dsl">
   * import ratpack.codahale.metrics.CodaHaleMetricsModule
   * import com.codahale.metrics.MetricRegistry
   * import static ratpack.groovy.Groovy.ratpack
   *
   * ratpack {
   *   modules {
   *     register new CodaHaleMetricsModule().jmx()
   *   }
   *
   *   handlers { MetricRegistry metricRegistry ->
   *     handler {
   *       metricRegistry.meter("my custom meter").mark()
   *       render ""
   *     }
   *   }
   * }
   * </pre>
   * <p>
   * Custom metrics can also be added via the Metrics annotations ({@link Metered}, {@link Timed} and {@link com.codahale.metrics.annotation.Gauge}) 
   * to any Guice injected classes.
   * Please see the <a href="https://github.com/ratpack/example-books" target="_blank">example-books</a> project for an example.
   * </p>
   *
   * @return this {@code CodaHaleMetricsModule}
   * @see <a href="http://metrics.codahale.com/getting-started/" target="_blank">Coda Hale Metrics - Getting Started</a>
   * @see ratpack.codahale.metrics.CodaHaleMetricsModule#jmx()
   * @see ratpack.codahale.metrics.CodaHaleMetricsModule#console()
   * @see CodaHaleMetricsModule#csv(java.io.File)
   */
  public CodaHaleMetricsModule metrics() {
    return metrics(true);
  }

  /**
   * Enables or disables the collecting of metrics.
   *
   * @param enabled If the metric collection should be enabled.
   * @return this {@code CodaHaleMetricsModule}
   * @see ratpack.codahale.metrics.CodaHaleMetricsModule#metrics()
   */
  public CodaHaleMetricsModule metrics(boolean enabled) {
    this.metricsEnabled = enabled;
    return this;
  }

  public CodaHaleMetricsModule healthChecks() {
    return healthChecks(true);
  }

  public CodaHaleMetricsModule healthChecks(boolean enabled) {
    this.healthChecksEnabled = enabled;
    return this;
  }

  public CodaHaleMetricsModule jvmMetrics() {
    return jvmMetrics(true);
  }

  public CodaHaleMetricsModule jvmMetrics(boolean enabled) {
    this.jvmMetricsEnabled = enabled;
    return this;
  }

  /**
   * Enable the reporting of metrics via JMX.  The collecting of metrics will also be enabled.
   *
   * @return this {@code CodaHaleMetricsModule}
   * @see <a href="http://metrics.codahale.com/getting-started/#reporting-via-jmx" target="_blank">Coda Hale Metrics - Reporting Via JMX</a>
   * @see ratpack.codahale.metrics.CodaHaleMetricsModule#console()
   * @see CodaHaleMetricsModule#csv(java.io.File)
   */
  public CodaHaleMetricsModule jmx() {
    return jmx(true);
  }

  /**
   * Enables or disables the reporting of metrics via JMX.
   *
   * @param enabled If JMX metric reporting should be enabled.
   * @return this {@code CodaHaleMetricsModule}
   * @see CodaHaleMetricsModule#jmx()
   */
  public CodaHaleMetricsModule jmx(boolean enabled) {
    this.reportMetricsToJmx = enabled;
    return this;
  }

  /**
   * Enable the reporting of metrics to the Console.  The collecting of metrics will also be enabled.
   *
   * @return this {@code CodaHaleMetricsModule}
   * @see <a href="http://metrics.codahale.com/manual/core/#man-core-reporters-console" target="_blank">Coda Hale Metrics - Console Reporting</a>
   * @see ratpack.codahale.metrics.CodaHaleMetricsModule#jmx()
   * @see CodaHaleMetricsModule#csv(java.io.File)
   */
  public CodaHaleMetricsModule console() {
    return console(true);
  }

  /**
   * Enables or disables the reporting of metrics to the Console.
   *
   * @param enabled If Console metric reporting should be enabled.
   * @return this {@code CodaHaleMetricsModule}
   * @see ratpack.codahale.metrics.CodaHaleMetricsModule#console()
   */
  public CodaHaleMetricsModule console(boolean enabled) {
    this.reportMetricsToConsole = enabled;
    return this;
  }

  /**
   * Enable the reporting of metrics to a CSV file.  The collecting of metrics will also be enabled.
   *
   * @param reportDirectory The directory in which to create the CSV report files.
   * @return this {@code CodaHaleMetricsModule}
   * @see <a href="http://metrics.codahale.com/manual/core/#man-core-reporters-csv" target="_blank">Coda Hale Metrics - CSV Reporting</a>
   * @see ratpack.codahale.metrics.CodaHaleMetricsModule#jmx()
   * @see ratpack.codahale.metrics.CodaHaleMetricsModule#console()
   */
  public CodaHaleMetricsModule csv(File reportDirectory) {
    if (reportDirectory == null) {
      throw new IllegalArgumentException("reportDirectory cannot be null");
    }

    csvReportDirectory = reportDirectory;
    return this;
  }

  @Override
  public Handler decorate(Injector injector, Handler handler) {
    if (healthChecksEnabled) {
      final HealthCheckRegistry registry = injector.getInstance(HealthCheckRegistry.class);
      GuiceUtil.eachOfType(injector, TypeLiteral.get(NamedHealthCheck.class), new Action<NamedHealthCheck>() {
        public void execute(NamedHealthCheck healthCheck) throws Exception {
          registry.register(healthCheck.getName(), healthCheck);
        }
      });
    }

    if (jvmMetricsEnabled) {
      final MetricRegistry metricRegistry = injector.getInstance(MetricRegistry.class);
      metricRegistry.registerAll(new GarbageCollectorMetricSet());
      metricRegistry.registerAll(new ThreadStatesGaugeSet());
      metricRegistry.registerAll(new MemoryUsageGaugeSet());
    }

    if (isMetricsEnabled()) {
      return new RequestTimingHandler(handler);
    } else {
      return handler;
    }
  }
}

