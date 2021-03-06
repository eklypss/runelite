/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import joptsimple.OptionSet;
import net.runelite.api.Client;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteModule;
import net.runelite.client.ui.ClientUI;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PluginManagerTest
{
	private static final String PLUGIN_PACKAGE = "net.runelite.client.plugins";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private RuneLite runelite;
	private Set<Class> pluginClasses;

	@Mock
	ClientUI clientUi;

	@Mock
	Client client;

	@Before
	public void before() throws IOException
	{
		RuneLite.setOptions(mock(OptionSet.class));

		Injector injector = Guice.createInjector(new RuneLiteModule(),
			BoundFieldModule.of(this));
		RuneLite.setInjector(injector);

		runelite = injector.getInstance(RuneLite.class);
		runelite.setGui(clientUi);

		// Find plugins we expect to have
		pluginClasses = new HashSet<>();
		Set<ClassInfo> classes = ClassPath.from(getClass().getClassLoader()).getTopLevelClassesRecursive(PLUGIN_PACKAGE);
		for (ClassInfo classInfo : classes)
		{
			Class<?> clazz = classInfo.load();
			PluginDescriptor pluginDescriptor = clazz.getAnnotation(PluginDescriptor.class);
			if (pluginDescriptor != null)
			{
				pluginClasses.add(clazz);
			}
		}

	}

	@Test
	public void testLoadPlugins() throws Exception
	{
		PluginManager pluginManager = new PluginManager();
		pluginManager.setOutdated(true);
		pluginManager.loadCorePlugins();
		Collection<Plugin> plugins = pluginManager.getPlugins();
		long expected = pluginClasses.stream()
			.map(cl -> (PluginDescriptor) cl.getAnnotation(PluginDescriptor.class))
			.filter(Objects::nonNull)
			.filter(pd -> pd.loadWhenOutdated())
			.count();
		assertEquals(expected, plugins.size());

		runelite.setClient(client);

		pluginManager = new PluginManager();
		pluginManager.loadCorePlugins();
		plugins = pluginManager.getPlugins();

		expected = pluginClasses.stream()
			.map(cl -> (PluginDescriptor) cl.getAnnotation(PluginDescriptor.class))
			.filter(Objects::nonNull)
			.filter(pd -> !pd.developerPlugin())
			.count();
		assertEquals(expected, plugins.size());
	}

	@Test
	public void dumpGraph() throws Exception
	{
		List<Module> modules = new ArrayList<>();
		modules.add(new GraphvizModule());
		modules.add(new RuneLiteModule());

		runelite.setClient(client);

		PluginManager pluginManager = new PluginManager();
		pluginManager.loadCorePlugins();
		for (Plugin p : pluginManager.getPlugins())
		{
			modules.add(p);
		}

		File file = folder.newFile();
		try (PrintWriter out = new PrintWriter(file, "UTF-8"))
		{
			Injector injector = Guice.createInjector(modules);
			GraphvizGrapher grapher = injector.getInstance(GraphvizGrapher.class);
			grapher.setOut(out);
			grapher.setRankdir("TB");
			grapher.graph(injector);
		}
	}

}
