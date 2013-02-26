package com.taobao.f2e;

import org.apache.commons.cli.*;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Extract dependencies from code files.
 *
 * @author yiminghe@gmail.com
 * @since 2012-08-07
 */
public class ExtractDependency {

    /**
     * whether overwrite module's file with module name added.
     */
    private boolean fixModuleName = false;

    /**
     * packages.
     */
    private Packages packages = new Packages();

    /**
     * exclude pattern for modules.
     */
    private Pattern excludePattern;

    /**
     * include pattern for modules.
     */
    private Pattern includePattern;

    /**
     * dependency output file.
     */
    private String output;

    /**
     * dependency 's output file encoding.
     */
    private String outputEncoding = "utf-8";

    /**
     * whether output compact module desc
     */
    private boolean compact = false;

    private HashMap<String, ArrayList<String>> dependencyCode = new HashMap<String, ArrayList<String>>();

    static String CODE_PREFIX = "/*Generated by KISSY Module Compiler*/\n" +
            "KISSY.config('modules', {\n";

    static String COMPACT_CODE_PREFIX = "/*Generated by KISSY Module Compiler*/\n" +
            "config({\n";

    static String CODE_SUFFIX = "\n});";

    /**
     * dom/src -> dom
     * event/src -> event
     */
    private ArrayList<RegReplace> nameMap = new ArrayList<RegReplace>();

    private static class RegReplace {
        Pattern reg;
        String replace;
    }

    public void setFixModuleName(boolean fixModuleName) {
        this.fixModuleName = fixModuleName;
    }

    public Packages getPackages() {
        return packages;
    }

    public void setExcludePattern(Pattern excludePattern) {
        this.excludePattern = excludePattern;
    }

    public void setIncludePattern(Pattern includePattern) {
        this.includePattern = includePattern;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public void setOutputEncoding(String outputEncoding) {
        this.outputEncoding = outputEncoding;
    }

    private void processSingle(String path) {

        // 没必要缓存，每次都构建
        Module m = this.getPackages().getModuleFromPath(path);

        if (m == null || !m.isValidFormat()) {
            System.out.println("invalid module: " + path);
            return;
        }

        m.completeModuleName(this.fixModuleName);

        String name = m.getName();

        if (excludePattern != null &&
                excludePattern.matcher(name).matches()) {
            return;
        }

        if (includePattern != null &&
                !includePattern.matcher(name).matches()) {
            return;
        }

        String[] requires = m.getRequires();
        if (requires.length > 0) {
            dependencyCode.put(name, new ArrayList<String>(Arrays.asList(requires)));
        }
    }

    /**
     * generate code list by module dependency map
     */
    private ArrayList<String> formCodeList() {
        ArrayList<String> codes = new ArrayList<String>();
        Set<String> keys = dependencyCode.keySet();
        for (String name : keys) {
            ArrayList<String> requires = dependencyCode.get(name);
            if (requires.size() > 0) {
                String allRs = "";
                for (String r : requires) {
                    if (r.startsWith("#")) {
                        allRs += "," + r.substring(1);
                    } else {
                        allRs += ",'" + r + "'";
                    }
                }
                codes.add("'" + name + "': {requires: [" + allRs.substring(1) + "]}");
            }
        }
        return codes;
    }

    private void putToDependency(HashMap<String, ArrayList<String>> dependencyCode2,
                                 String name, ArrayList<String> requires) {

        ArrayList<String> old = dependencyCode2.get(name);

        if (old == null) {
            old = new ArrayList<String>();
        } else {
            // 防止循环引用
//            for (String require : old) {
//                if (require.equals(name)) {
//                    old.remove(require);
//                }
//            }
        }

        for (String require : requires) {
            if (!old.contains(require) &&
                    // 防止循环引用
                    !require.equals(name)) {
                old.add(require);
            }
        }

        dependencyCode2.put(name, old);
    }

    private String transformByNameMap(String name) {
        for (RegReplace rr : nameMap) {
            Pattern matchReg = rr.reg;
            if (matchReg.matcher(name).matches()) {
                return matchReg.matcher(name).replaceAll(rr.replace);
            }
        }
        return name;
    }

    private ArrayList<String> transformByNameMap(ArrayList<String> requires) {
        for (int i = 0; i < requires.size(); i++) {
            requires.set(i, transformByNameMap(requires.get(i)));
        }
        return requires;
    }

    /**
     * merge dependency within nameMap
     */
    private void mergeNameMap() {
        if (nameMap != null) {
            HashMap<String, ArrayList<String>> dependencyCode2 = new HashMap<String, ArrayList<String>>();
            Set<String> keys = dependencyCode.keySet();
            for (String name : keys) {
                putToDependency(dependencyCode2, transformByNameMap(name),
                        transformByNameMap(dependencyCode.get(name)));
            }

            this.dependencyCode = dependencyCode2;
        }
    }

    public static void commandRunnerCLI(String[] args) throws Exception {

        Options options = new Options();
        options.addOption("encodings", true, "baseUrls 's encodings");
        options.addOption("baseUrls", true, "baseUrls");
        options.addOption("excludeReg", true, "excludeReg");
        options.addOption("includeReg", true, "includeReg");
        options.addOption("nameMap", true, "nameMap");
        options.addOption("output", true, "output");
        options.addOption("v", "version", false, "version");
        options.addOption("outputEncoding", true, "outputEncoding");
        options.addOption("compact", true, "compact mode");
        options.addOption("fixModuleName", true, "fixModuleName");

        // create the command line parser
        CommandLineParser parser = new GnuParser();
        CommandLine line;

        try {
            // parse the command line arguments
            line = parser.parse(options, args);
        } catch (ParseException exp) {
            System.out.println("Unexpected exception:" + exp.getMessage());
            return;
        }

        if (line.hasOption("v")) {
            System.out.println("KISSY Dependency Extractor 1.3.1");
            return;
        }

        ExtractDependency m = new ExtractDependency();

        Packages packages = m.getPackages();

        String encodingStr = line.getOptionValue("encodings");
        if (encodingStr != null) {
            packages.setEncodings(encodingStr.split(","));
        }

        String baseUrlStr = line.getOptionValue("baseUrls");
        if (baseUrlStr != null) {
            packages.setBaseUrls(baseUrlStr.split(","));
        }

        String compact = line.getOptionValue("compact");
        if (compact != null) {
            m.compact = true;
        }

        String fixModuleName = line.getOptionValue("fixModuleName");
        if (fixModuleName != null && fixModuleName.equals("true")) {
            m.setFixModuleName(true);
        }

        String excludeReg = line.getOptionValue("excludeReg");
        if (excludeReg != null) {
            excludeReg = excludeReg.replaceAll("\\s", "");
            m.setExcludePattern(Pattern.compile(excludeReg));
        }

        String includeReg = line.getOptionValue("includeReg");
        if (includeReg != null) {
            includeReg = includeReg.replaceAll("\\s", "");
            m.setIncludePattern(Pattern.compile(includeReg));
        }

        m.setOutput(line.getOptionValue("output"));

        String outputEncoding = line.getOptionValue("outputEncoding");
        if (outputEncoding != null) {
            m.setOutputEncoding(outputEncoding);
        }

        String nameMapStr = line.getOptionValue("nameMap");

        if (nameMapStr != null) {
            m.constructNameMapFromString(nameMapStr);
        }

        m.run();

    }

    void constructNameMapFromString(String nameMapStr) {
        String[] names = nameMapStr.split(",");
        for (String n : names) {
            String[] ns = n.split("\\|\\|");
            RegReplace rr = new RegReplace();
            rr.reg = Pattern.compile(ns[0]);
            rr.replace = ns[1];
            nameMap.add(rr);
        }
    }

    public void run() {
        long start = System.currentTimeMillis();
        String[] baseUrls = packages.getBaseUrls();

        for (String baseUrl : baseUrls) {
            Collection<File> files = org.apache.commons.io.FileUtils.listFiles(new File(baseUrl),
                    new String[]{"js"}, true);

            for (File f : files) {
                processSingle(f.getPath());
            }
        }

        // merge by name map
        mergeNameMap();

        // get code list
        ArrayList<String> codes = formCodeList();

        if (codes.size() > 0) {
            // serialize to file
            FileUtils.outputContent(
                    (this.compact ? COMPACT_CODE_PREFIX : CODE_PREFIX) +
                            ArrayUtils.join(codes.toArray(new String[codes.size()]), ",\n")
                            + CODE_SUFFIX
                    , output, outputEncoding);
            System.out.println("dependency file saved to: " + output);
        }

        System.out.println("duration: " + (System.currentTimeMillis() - start));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("current path: " + new File(".").getAbsolutePath());
        System.out.println("current args: " + Arrays.toString(args));
        commandRunnerCLI(args);
    }
}
