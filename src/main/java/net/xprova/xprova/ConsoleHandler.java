package net.xprova.xprova;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import net.xprova.deprecated.CodeSimulator;
import net.xprova.dot.GraphDotPrinter;
import net.xprova.graph.Graph;
import net.xprova.netlist.GateLibrary;
import net.xprova.netlist.Netlist;
import net.xprova.netlistgraph.Generator;
import net.xprova.netlistgraph.NetlistGraph;
import net.xprova.netlistgraph.NetlistGraphDotFormatter;
import net.xprova.netlistgraph.Vertex;
import net.xprova.piccolo.Command;
import net.xprova.piccolo.Console;
import net.xprova.propertylanguage.PropertyLanguageTest;
import net.xprova.simulations.CodeGenerator;
import net.xprova.verilogparser.VerilogParser;

public class ConsoleHandler {

	private GateLibrary lib = null;

	private NetlistGraph graph = null;

	private PrintStream out = null;

	private HashMap<String, FlipFlop> defsFF = null;

	public ConsoleHandler(PrintStream ps) {

		out = ps;

		defsFF = new HashMap<String, FlipFlop>();

	}

	@Command(aliases = { "load_lib", "ll" }, description = "load a cell library")
	public void loadLibrary(String libFile) throws Exception {

		ArrayList<Netlist> libMods = VerilogParser.parseFile(libFile, new GateLibrary());

		lib = new GateLibrary(libMods);

		out.printf("Loaded %d modules from library %s\n", libMods.size(), libFile);

	}

	@Command(aliases = { "list_modules" }, description = "list the contents of the loaded cell library")
	public void reportLibrary() {

		if (lib == null) {

			out.println("No library is currently loaded");

		} else {

			lib.printModules();

		}

	}

	@Command(aliases = { "read_verilog"}, description = "read a gate-level verilog netlist")
	public void readVerilogFile(String args[]) throws Exception {

		// parse command input

		Option optModule = Option.builder("m").desc("name of module").hasArg().argName("MODULE").required(false)
				.build();

		Options options = new Options();

		options.addOption(optModule);

		CommandLineParser parser = new DefaultParser();

		CommandLine line;

		line = parser.parse(options, args);

		if (line.getArgList().isEmpty())
			throw new Exception("no file specified");

		// use first non-empty argument as file name

		String verilogFile = "";

		for (String str : line.getArgList())
			if (!str.isEmpty())
				verilogFile = str.trim();

		Netlist selNetlist = null;

		// read verilog file

		if (lib == null) {

			out.println("No library is current loaded");

		} else {

			ArrayList<Netlist> nls = VerilogParser.parseFile(verilogFile, lib);

			String moduleName = line.getOptionValue("m", nls.get(0).name);

			for (Netlist nl : nls) {

				if (nl.name.equals(moduleName)) {

					selNetlist = nl;

					break;

				}

			}

			if (selNetlist == null)
				throw new Exception(String.format("Cannot find module <%s> in file <%s>", moduleName, verilogFile));

			graph = new NetlistGraph(selNetlist);

			out.printf("Loaded design <%s>\n", selNetlist.name);

		}

	}

	@Command(aliases = { "write_verilog" }, description = "export current design as a verilog netlist")
	public void writeVerilogFile(String args[]) throws Exception {

		if (args.length != 1) {

			throw new Exception("requires one argument: filename");

		}

		String outputVerilogFile = args[0];

		Generator.generateFile(graph, outputVerilogFile);
	}

	@Command(aliases = {
			"export_dot" }, description = "export a graph representation of the loaded design in DOT format")
	public void exportDotFile(String[] args) throws Exception {

		// parse command input

		Option optIgnoreEdges = Option.builder().desc("list of edges to ignore").hasArg().argName("IGNORE_EDGES")
				.required(false).longOpt("ignore-edges").build();

		Option optIgnoreVertices = Option.builder().desc("list of vertices to ignore").hasArg()
				.argName("IGNORE_VERTICES").required(false).longOpt("ignore-vertices").build();

		Option optType = Option.builder("t").longOpt("type").hasArg().desc("graph elements: ([n]ets|[g]ates|[f]lops)")
				.build();

		Option optTo = Option.builder().longOpt("to").hasArg().desc("destination flip-flop").build();

		Option optCombine = Option.builder().longOpt("combine").hasArg().desc("list of vertex prefixes to combine")
				.build();

		Options options = new Options();

		options.addOption(optIgnoreEdges);

		options.addOption(optIgnoreVertices);

		options.addOption(optType);

		options.addOption(optTo);

		options.addOption(optCombine);

		CommandLineParser parser = new DefaultParser();

		CommandLine line = parser.parse(options, args);

		if (line.getArgList().isEmpty())
			throw new Exception("no file specified");

		// use first non-empty argument as file name

		String dotFile = "";

		for (String str : line.getArgList())
			if (!str.isEmpty())
				dotFile = str.trim();

		// produce graph

		if (graph == null) {

			out.println("No design is currently loaded");

			return;

		}

		NetlistGraph effectiveNetlist;

		if (line.hasOption("to")) {

			String vName = line.getOptionValue("to");

			Vertex flop = graph.getVertex(vName);

			if (flop == null) {

				throw new Exception(
						String.format("netlist <%s> does not contain flip-flip <%s>", graph.getName(), vName));

			}

			HashSet<Vertex> flipflops = graph.getModulesByTypes(defsFF.keySet());

			HashSet<Vertex> flopInputVertices = graph.bfs(flop, flipflops, true);

			Graph<Vertex> flopGraph = graph.reduce(flipflops);

			flopInputVertices.add(flop);

			flopInputVertices.addAll(flopGraph.getSources(flop));

			effectiveNetlist = graph.getSubGraph(flopInputVertices);

		} else {

			effectiveNetlist = graph;

		}

		HashSet<Vertex> selectedVertices;

		if (!line.hasOption('t')) {

			// no type specified, include all vertex types (net, gate,
			// flip-flop)

			selectedVertices = effectiveNetlist.getVertices();

		} else {

			// vertex types were specified

			String vTypes = line.getOptionValue('t');

			selectedVertices = new HashSet<Vertex>();

			HashSet<Vertex> flipflops = graph.getModulesByTypes(defsFF.keySet());

			// are flipflops are flipflops2 the same?

			HashSet<Vertex> gates = effectiveNetlist.getModules();

			gates.removeAll(flipflops);

			if (vTypes.contains("n"))
				selectedVertices.addAll(effectiveNetlist.getNets());

			if (vTypes.contains("f"))
				selectedVertices.addAll(flipflops);

			if (vTypes.contains("g"))
				selectedVertices.addAll(gates);

		}

		Graph<Vertex> sg = effectiveNetlist.reduce(selectedVertices);

		if (line.hasOption("combine")) {

			for (String s : line.getOptionValue("combine").split(","))
				combineVertices(s, selectedVertices, effectiveNetlist, sg);

		}

		NetlistGraphDotFormatter formatter = new NetlistGraphDotFormatter(graph);

		if (line.hasOption("ignore-edges")) {

			formatter.setIgnoredEdges(line.getOptionValue("ignore-edges").split(","));

		}

		if (line.hasOption("ignore-vertices")) {

			formatter.setIgnoredVertices(line.getOptionValue("ignore-vertices").split(","));

		}

		GraphDotPrinter.printGraph(dotFile, sg, formatter, selectedVertices);

	}

	private void combineVertices(String prefix, HashSet<Vertex> selectedVertices, NetlistGraph effectiveNetlist,
			Graph<Vertex> sg) throws Exception {

		HashSet<Vertex> grp1 = new HashSet<Vertex>();

		HashSet<String> grp1_types = new HashSet<String>();

		Vertex comb = null;

		int n = prefix.length();

		for (Vertex v : selectedVertices) {

			String upToNCharacters = v.name.substring(0, Math.min(v.name.length(), n));

			if (upToNCharacters.equals(prefix)) {

				grp1.add(v);

				grp1_types.add(v.subtype);

				comb = v;

			}

		}

		if (!grp1.isEmpty()) {

			grp1.remove(comb);

			selectedVertices.removeAll(grp1);

			comb.name = prefix;

			if (grp1_types.size() > 1) {

				comb.subtype = "BLOCK";

			}

			sg.combineVertices(comb, grp1);

		}

	}

	@Command(aliases = { "augment_netlist",
			"aug" }, description = "add metastable flip-flop models and associated connections")
	public void augmentNetlist() throws Exception {

		if (graph == null) {

			out.println("No design is currently loaded");

		} else {

			Transformer t1 = new Transformer(graph, defsFF);

			t1.transformCDC();

		}

	}

	@Command(aliases = { "clear_ff_defs" }, description = "clear flip-flop definitions")
	public void clearDefsFF(String[] args) {

		// specify flip-flop modules

		defsFF.clear();

	}

	@Command(aliases = { "def_ff" }, description = "define and specify the ports of a flip-flop cell")
	public void addDefFF(String modName, String clkPort, String rstPort, String dPort) throws Exception {

		if (defsFF.containsKey(modName)) {

			throw new Exception(String.format("definition already exists for flip-flop <%s> ", modName));

		} else {

			FlipFlop ff = new FlipFlop(modName, clkPort, rstPort, dPort);

			defsFF.put(modName, ff);

			out.printf("defined flip-flop <%s>\n", modName);

		}

	}

	@Command(aliases = { "list_ff" }, description = "list flip-flop definitions")
	public void listDefFF() {

		String strFormat = "%20s %14s %14s %14s\n";

		out.print(String.format(strFormat, "Flip-flip Module", "Clock Port", "Reset Port", "Data Port"));
		out.print(String.format(strFormat, "----------------", "----------", "----------", "---------"));

		for (FlipFlop f : defsFF.values()) {

			out.print(String.format(strFormat, f.moduleName, f.clkPort, f.rstPort, f.dPort));

		}

	}

	@Command(aliases = { "report_domains" }, description = "print list of clock domains in current design")
	public void reportClockDomains() throws Exception {

		Transformer t1 = new Transformer(graph, defsFF);

		HashSet<Vertex> clks = t1.getClocks();

		String strFormat = "%20s %14s\n";

		out.printf("Found %d clock domain(s):\n\n", clks.size());

		out.print(String.format(strFormat, "Clock Domain Net", "  FFs"));
		out.print(String.format(strFormat, "----------------", "-----"));

		for (Vertex clk : clks) {

			int count = t1.getDomainFlops(clk).size();

			out.print(String.format(strFormat, clk, count));

		}

	}

	@Command(aliases = { "report_xpaths" }, description = "print list of crossover paths in current design")
	public void reportCrossoverPaths() {

		String strFormat = "%20s -> %20s";

		ArrayList<String> paths = new ArrayList<String>();

		HashSet<Vertex> flops = graph.getModulesByTypes(defsFF.keySet());

		Graph<Vertex> ffGraph = graph.reduce(flops);

		for (Vertex src : flops) {

			for (Vertex dst : ffGraph.getDestinations(src)) {

				Vertex clk1 = graph.getNet(src, defsFF.get(src.subtype).clkPort);
				Vertex clk2 = graph.getNet(dst, defsFF.get(dst.subtype).clkPort);

				if (clk1 != clk2)
					paths.add(String.format(strFormat, src, dst));

			}

		}

		Collections.sort(paths);

		// output

		out.printf("Found %d crossover paths:\n\n", paths.size());

		for (String p : paths) {

			out.println(p);

		}

	}

	@Command(aliases = { "rename_modules" }, description = "rename modules according to their types")
	public void renameModules(String args[]) throws ParseException {

		// rename modules

		final String modStr = "%s%d";

		// parse input

		Option optIgnore = Option.builder().desc("list of nets to ignore").hasArg().argName("IGNORE_NETS")
				.required(false).longOpt("ignore").build();

		Options options = new Options();

		options.addOption(optIgnore);

		CommandLineParser parser = new DefaultParser();

		CommandLine line = parser.parse(options, args);

		String ignoreStr = line.getOptionValue("ignore", "");

		HashSet<String> ignored = new HashSet<String>(Arrays.asList(ignoreStr.split(",")));

		List<Vertex> sortedMods = new ArrayList<Vertex>(graph.getModules());

		Collections.sort(sortedMods, new Comparator<Vertex>() {

			@Override
			public int compare(Vertex arg0, Vertex arg1) {

				return arg0.name.compareTo(arg1.name);
			}

		});

		HashMap<String, Integer> modCounts = new HashMap<String, Integer>();

		for (Vertex v : sortedMods) {

			if (!ignored.contains(v.name)) {

				String lowerMod = v.subtype.toLowerCase();

				Integer c = modCounts.get(lowerMod);

				c = (c == null) ? 1 : c + 1;

				v.name = String.format(modStr, lowerMod, c);

				modCounts.put(lowerMod, c);

			}

		}

	}

	@Command(aliases = { "rename_nets" }, description = "rename nets as n1, n2 ...")
	public void renameNets(String args[]) throws ParseException {

		// renames internal (i.e. non-port) nets

		final String netStr = "n%d";

		// parse input

		Option optIgnore = Option.builder().desc("list of nets to ignore").hasArg().argName("IGNORE_NETS")
				.required(false).longOpt("ignore").build();

		Options options = new Options();

		options.addOption(optIgnore);

		CommandLineParser parser = new DefaultParser();

		CommandLine line = parser.parse(options, args);

		String ignoreStr = line.getOptionValue("ignore", "");

		HashSet<String> ignored = new HashSet<String>(Arrays.asList(ignoreStr.split(",")));

		// rename nets

		int netCount = 0;

		HashSet<Vertex> nonIO = graph.getNets();

		nonIO.removeAll(graph.getIONets());

		List<Vertex> nonIOList = new ArrayList<Vertex>(nonIO);

		Collections.sort(nonIOList, new Comparator<Vertex>() {

			@Override
			public int compare(Vertex arg0, Vertex arg1) {

				return arg0.name.compareTo(arg1.name);
			}

		});

		for (Vertex n : nonIOList) {

			if (!ignored.contains(n.name)) {

				netCount += 1;

				n.name = String.format(netStr, netCount);

			}

		}

	}

	@Command(aliases = { "prove" }, description = "attempt to prove (assumption -> valid) in the current design")
	public void proveAssertions(String args[]) throws Exception {

		// options:
		// --printcode
		// --norun

		// parse command input

		Option optPrintCode = Option.builder().desc("print generated code").required(false).longOpt("printcode")
				.build();

		Option optNoRun = Option.builder().desc("do not execute generated code").required(false).longOpt("norun")
				.build();

		Option optNoCounter = Option.builder().desc("do not print counter-example (if found)").required(false)
				.longOpt("nocounter").build();

		Options options = new Options();

		options.addOption(optPrintCode);

		options.addOption(optNoRun);

		options.addOption(optNoCounter);

		CommandLineParser parser = new DefaultParser();

		CommandLine line = parser.parse(options, args);

		// code:

		if (graph == null)
			throw new Exception("no design is loaded");

		String invokeArgs = line.hasOption("nocounter") ? "" : "gen-counter-example";

		String templateResourceFile = "template1.j";

		String javaFileStr = "CodeSimulator.java";

		String classFileStr = "CodeSimulator";

		File tempDir = new File(System.getProperty("java.io.tmpdir"));

		File javaFile = new File(tempDir, javaFileStr);

		String cmd = "javac " + javaFile.getAbsolutePath();

		String cmd2 = String.format("java -classpath %s %s %s", tempDir.getAbsolutePath(), classFileStr, invokeArgs);

		// generate code

		String templateCode = loadResourceString(templateResourceFile);

		ArrayList<String> lines = (new CodeGenerator(graph)).generate(templateCode);

		if (line.hasOption("printcode")) {

			for (String l : lines)
				out.println(l);

		}

		if (!line.hasOption("norun")) {

			out.println("Saving code to " + javaFile.getAbsolutePath() + " ...");

			PrintStream fout = new PrintStream(javaFile);

			for (String l : lines)
				fout.println(l);

			fout.close();

			// compile using javac

			out.println("Compiling ...");

			final Runtime rt = Runtime.getRuntime();

			Process proc = rt.exec(cmd);

			proc.waitFor();

			if (proc.exitValue() != 0) {

				BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

				BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

				String s = null;

				while ((s = stdInput.readLine()) != null)
					out.println(s);

				while ((s = stdError.readLine()) != null)
					out.println(s);

				throw new Exception("Compilation failed");

			}

			// run code

			out.println("Executing compiled code ...");

			Process proc2 = rt.exec(cmd2);

			proc.waitFor();

			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc2.getInputStream()));

			BufferedReader stdError = new BufferedReader(new InputStreamReader(proc2.getErrorStream()));

			String s = null;

			while ((s = stdInput.readLine()) != null)
				out.println(s);

			while ((s = stdError.readLine()) != null)
				out.println(s);

		}

	}

	@Deprecated
	public void testCode(int printCounterExample) throws Exception {

		CodeSimulator sim1 = new CodeSimulator();

		int initial = sim1.getResetState();

		if (printCounterExample == 1) {

			int[] counterExample = sim1.exploreSpace(initial);

			if (counterExample != null)
				sim1.simulate(initial, counterExample);

		} else {

			sim1.exploreSpace(initial);

		}

	}

	private static String loadResourceString(String file) {

		Scanner s = null;

		String bannerFileContent;

		try {

			final InputStream stream;

			stream = Console.class.getClassLoader().getResourceAsStream(file);

			s = new Scanner(stream);

			bannerFileContent = s.useDelimiter("\\Z").next();

		} catch (Exception e) {

			bannerFileContent = "<could not load internal file>\n";

		} finally {

			if (s != null)
				s.close();

		}

		return bannerFileContent;

	}

	@Command(aliases = {"ungroup_nets"}, description = "split arrays into individual nets")
	public void ungroupNets(String args[]) {

		String netNameFormat = args.length == 0 ? "%s_%d_" : String.join(" ", args);

		for (Vertex v : graph.getNets()) {

			int k1 = v.name.indexOf("[");
			int k2 = v.name.indexOf("]");

			String name = v.name;

			int bit = 0;

			if (k1 != -1 && k2 != -1) {

				name = v.name.substring(0, k1);

				bit = Integer.valueOf(v.name.substring(k1 + 1, k2));

				v.name = String.format(netNameFormat, name, bit);
			}

		}

	}

	@Command(aliases = { "synth_verilog" }, description = "synthesize behavioral verilog design using yosys")
	public void synthBehavioralDesign(String args[]) throws Exception {

		// TODO: implement the following switches
		// -v : verbose mode, show output of yosys
		// --lib myfile.lib: specify custom library for yosys

		String behavioralDesign = args[0];

		String synthDesign = args[1];

		(new SynthesisEngine()).synthesis(behavioralDesign, synthDesign, out);

	}

	@Command
	public void testPL() {

		(new PropertyLanguageTest()).test();

	}

}
