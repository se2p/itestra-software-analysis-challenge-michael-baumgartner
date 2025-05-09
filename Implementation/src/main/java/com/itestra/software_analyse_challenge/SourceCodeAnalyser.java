package com.itestra.software_analyse_challenge;

import org.apache.commons.cli.*;

import java.io.*;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

        /*
         * Assumption: Directory is always the java source directory
         * where the first level of subdirectories are the projects / root packages to consider.
         */
        List<String> rootPackages = Arrays.stream(Objects.requireNonNull(
                    input.getInputDirectory().listFiles(File::isDirectory)
                )).map(File::getName).toList();

        Map<File, List<String>> imports = new HashMap<>(files.size());
        Map<File, Set<String>> directDependencies = new HashMap<>(files.size());
        Map<File, Set<String>> indirectDependencies = new HashMap<>(files.size());
        Map<File, List<File>> dependents = new HashMap<>(files.size());
        for (File file: files) {
            indirectDependencies.put(file, new HashSet<>(rootPackages.size()));
            dependents.put(file, new LinkedList<>());
        }
        for (File file : files) {
            imports.put(file, getFileImports(file));
            /*
             * Assumption: Directory is always the java source directory
             * where the first level of subdirectories are the projects / root packages to consider.
             */
            List<String> otherRootPackages = rootPackages.stream()
                    .filter(p -> !file.getPath().startsWith(input.getInputDirectory()
                            + File.separator + p.replaceAll("\\.", File.separator)))
                    .toList();
            directDependencies.put(file, analyseDirectDependencies(imports.get(file), otherRootPackages));
            imports.get(file).forEach(imported -> {
                File dependency = new File(input.getInputDirectory() + File.separator
                        + imported.replaceAll("\\.", File.separator) + ".java");
                if (dependents.containsKey(dependency)) {
                    dependents.get(dependency).add(file);
                }
            });
        }
        for (File file : files) {
            if (!directDependencies.get(file).isEmpty()) {
                addDependenciesToDependents(file, indirectDependencies, directDependencies, dependents);
            }
        }
        // Put all dependencies together
        indirectDependencies.forEach((file, set) -> set.addAll(directDependencies.get(file)));


        // For each file put one Output object to your result map.
        Map<String, Output> output = new HashMap<>(files.size());
        for (File file : files) {
            output.put(file.getName(), new Output(analyseSLOC(file, false),
                    indirectDependencies.get(file).stream().toList()));
            // You can extend the Output object using the functions lineNumberBonus(int), if you did
            // the bonus exercise.
            output.get(file.getName()).lineNumberBonus(analyseSLOC(file, true));
        }
        return output;
    }


    /**
     * 1. Analyze the number of source lines <br>
     * OR <br>
     * 3. BONUS: Analyze the number of source lines excluding getters and block comments
     *
     * @param file File to analyse
     * @param enhanced If True, further excluding of lines for the task 3
     *                 otherwise counting of task 1.
     * @return Line number of the given file
     */
    private static int analyseSLOC(File file, boolean enhanced) {
        if (file.canRead()) {
            int lineNumber = 0;
            boolean insideMultilineString = false;
            boolean multilineStringFirstLine = false;
            // Does a multiline string start? (Not inside a single line comment or start of block comment!)
            Pattern multilineStringStart = Pattern.compile("^((?!/[/*]).)*\"\"\".*$");
            // Does a multiline string end? (Cannot be inside a single line comment)
            Pattern multilineStringEnd = Pattern.compile("^.*\"\"\".*$");

            boolean insideBlockComment = false;
            // Does a block comment start? (And no line comment i.e., //* or // ... /*)
            Pattern blockCommentStart = Pattern.compile("^(((?!//).)*[^/]|)(?<comment>/\\*).*$");
            // Does a block commend end? (Cannot be inside a single line comment)
            Pattern blockCommentEnd = Pattern.compile("^.*(?<comment>\\*/).*$");

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    MatchResult blockCommentStartMatch = null;
                    if (!insideMultilineString && !insideBlockComment) {
                        // Check for multiline string first, as it consider not to be in a block comment
                        if (multilineStringStart.matcher(line).matches()) {
                            insideMultilineString = true;
                            multilineStringFirstLine = true;
                        } else if (enhanced) {
                            Matcher blockCommentStartMatcher = blockCommentStart.matcher(line);
                            if (blockCommentStartMatcher.matches()) {
                                blockCommentStartMatch = blockCommentStartMatcher.toMatchResult();
                                insideBlockComment = true;
                                if (blockCommentStartMatch.start("comment") != 0) {
                                    // block comment starts after a source code line --> count this line
                                    ++lineNumber;

                                }
                            }
                        }
                    }
                    if (!insideBlockComment && !line.isEmpty()
                            // "lines containing comments" --> Line which is only a comment
                            && !(line.startsWith("//") && !insideMultilineString)) {
                        ++lineNumber;
                    }
                    if (insideMultilineString && !multilineStringFirstLine
                            && multilineStringEnd.matcher(line).matches()) {
                        insideMultilineString = false;
                    }
                    if (multilineStringFirstLine) {
                        multilineStringFirstLine = false;
                    }
                    if (insideBlockComment) {
                        Matcher blockCommentEndMatcher = blockCommentEnd.matcher(line);
                        if (blockCommentEndMatcher.matches()) {
                            MatchResult blockCommentEndMatch = blockCommentEndMatcher.toMatchResult();
                            /*/ A block comment does not end if it shares its * with the start comment,
                                but a / before the end comment in general is okay, see here: /*/
                            if (!(blockCommentStartMatch != null && blockCommentStartMatch.end("comment")
                                    == blockCommentEndMatch.start("comment"))) {
                                insideBlockComment = false;
                                if (blockCommentEndMatch.end("comment") == line.length() - 1) {
                                    /*/ block comment before source code line --> count this line /*/
                                    ++lineNumber;
                                }
                            }
                        }
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
     * 2. Analyze the project dependencies
     *
     * @param fileImports Import of a File which dependencies are of interest
     * @param packages List of package names
     * @return {@code packages} filtered that only the one remains on which {@code file} directly depends on
     */
    private static Set<String> analyseDirectDependencies(List<String> fileImports, List<String> packages) {
            Set<String> dependencies = new HashSet<>(packages.size());
            for (String fileImport : fileImports) {
                for (String pkg : packages) {
                    if (!dependencies.contains(pkg) && fileImport.startsWith(pkg)) {
                        dependencies.add(pkg);
                    }
                }
                // All packages already a dependency?
                if (dependencies.size() == packages.size()) {
                    break;
                }
            }
            return dependencies;
    }

    private static void addDependenciesToDependents(File file, Map<File, Set<String>> indirectDependencies,
            Map<File, Set<String>> directDependencies, Map<File, List<File>> dependents) {
        for (File dependent : dependents.get(file)) {
            indirectDependencies.get(dependent).addAll(directDependencies.get(file));
            addDependenciesToDependents(dependent, indirectDependencies, directDependencies, dependents);
        }
    }
    /**
     * Reads the imports from a Java file
     *
     * @param file to consider
     * @return All imported classes in this file
     */
    private static List<String> getFileImports(File file) {
        if (file.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                List<String> imports = new LinkedList<>();
                Pattern importRegEx = Pattern.compile("^ *import ([a-zA-Z_.]+); *$");
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = importRegEx.matcher(line);
                    if (matcher.find()) {
                        imports.add(matcher.group(1));
                    }
                }
                return imports;
            } catch (IOException e) {
                // The given Output type does not consider the case of "No analyse possible".
                return Collections.emptyList();
            }
        } else {
            // The given Output type does not consider the case of "No analyse possible".
            return Collections.emptyList();
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
