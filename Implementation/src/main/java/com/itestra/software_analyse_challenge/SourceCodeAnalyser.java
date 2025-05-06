package com.itestra.software_analyse_challenge;

import org.apache.commons.cli.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class SourceCodeAnalyser {

    /**
     * Your implementation
     *
     * @param input {@link Input} object.
     * @return mapping from filename -> {@link Output} object.
     */
    public static Map<String, Output> analyse(Input input) {
        List<File> files = getFilesInDirectory(input.getInputDirectory());

        // For each file put one Output object to your result map.
        Map<String, Output> output = new HashMap<>(files.size());
        for (File file : files) {
            output.put(file.getName(), new Output(analyseSLOC(file), null));
        }

        // You can extend the Output object using the functions lineNumberBonus(int), if you did
        // the bonus exercise.

        return output;
    }

    /**
     * 1. Analyze the number of source lines
     * @param file File to analyse
     * @return Line number of the given file
     */
    private static int analyseSLOC(File file) {
        if (file.canRead()) {
            int lineNumber = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // "lines containing comments" --> Line which is only a comment
                    if (!line.isEmpty() && !line.startsWith("//")) {
                        ++lineNumber;
                    }
                }
            } catch (FileNotFoundException e) {
                // The given Output type does not consider the case of "No analyse possible".
                return -37;
            } catch (IOException e) {
                // The given Output type does not consider the case of "No analyse possible".
                return -73;
            }
            return lineNumber;
        } else {
            // The given Output type does not consider the case of "No analyse possible".
            return -42;
        }
    }

    /**
     * Returns all files in a directory and recursively its subdirectories.
     * @param directory The directory to look at
     * @return The List of Files found (without directories)
     */
    private static List<File> getFilesInDirectory(File directory) {
        return Arrays.stream(Objects.requireNonNull(directory.listFiles()))
            .<File>mapMulti((f, c) -> {
                if (f.isDirectory()) {
                    getFilesInDirectory(f).forEach(c);
                } else {
                    c.accept(f);
                }
            }).toList();
    }


    /*
     * INPUT - OUTPUT
     *
     * No changes below here are necessary!
     */

    public static final Option INPUT_DIR = Option.builder("i")
            .longOpt("input-dir")
            .hasArg(true)
            .desc("input directory path")
            .required(false)
            .build();

    public static final String DEFAULT_INPUT_DIR = String.join(File.separator , Arrays.asList("..", "CodeExamples", "src", "main", "java"));

    private static Input parseInput(String[] args) {
        Options options = new Options();
        Collections.singletonList(INPUT_DIR).forEach(options::addOption);
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            CommandLine commandLine = parser.parse(options, args);
            return new Input(commandLine);
        } catch (ParseException e) {
            formatter.printHelp("help", options);
            throw new IllegalStateException("Could not parse Command Line", e);
        }
    }

    private static void printOutput(Map<String, Output> outputMap) {
        System.out.println("Result: ");
        List<OutputLine> outputLines =
                outputMap.entrySet().stream()
                        .map(e -> new OutputLine(e.getKey(), e.getValue().getLineNumber(), e.getValue().getLineNumberBonus(), e.getValue().getDependencies()))
                        .sorted(Comparator.comparing(OutputLine::getFileName))
                        .collect(Collectors.toList());
        outputLines.addFirst(new OutputLine("File", "Source Lines", "Source Lines without Getters and Block Comments", "Dependencies"));
        int maxDirectoryName = outputLines.stream().map(OutputLine::getFileName).mapToInt(String::length).max().orElse(100);
        int maxLineNumber = outputLines.stream().map(OutputLine::getLineNumber).mapToInt(String::length).max().orElse(100);
        int maxLineNumberWithoutGetterAndSetter = outputLines.stream().map(OutputLine::getLineNumberWithoutGetterSetter).mapToInt(String::length).max().orElse(100);
        int maxDependencies = outputLines.stream().map(OutputLine::getDependencies).mapToInt(String::length).max().orElse(100);
        String lineFormat = "| %"+ maxDirectoryName+"s | %"+maxLineNumber+"s | %"+maxLineNumberWithoutGetterAndSetter+"s | %"+ maxDependencies+"s |%n";
        outputLines.forEach(line -> System.out.printf(lineFormat, line.getFileName(), line.getLineNumber(), line.getLineNumberWithoutGetterSetter(), line.getDependencies()));
    }

    public static void main(String[] args) {
        Input input = parseInput(args);
        Map<String, Output> outputMap = analyse(input);
        printOutput(outputMap);
    }
}
