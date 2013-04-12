package at.ac.tuwien.infosys.www.pixy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import at.ac.tuwien.infosys.www.pixy.conversion.BuiltinFunctions;
import at.ac.tuwien.infosys.www.pixy.conversion.InternalStrings;
import at.ac.tuwien.infosys.www.pixy.conversion.TacOperators;

public class MyOptions {

    // various boolean options and their defaults;
    // explanations are given in the cli help output (see main method of Checker)
    public static boolean optionA = false;
    public static boolean option_A = false;
    public static boolean optionB = false;
    public static boolean optionC = false;
    public static boolean optionD = false;
    public static boolean optionF = false;
    public static boolean optionG = true;
    public static boolean optionI = false;
    public static boolean optionL = false;
    public static boolean option_L = false;
    public static boolean optionM = false;
    public static boolean optionP = false;
    public static boolean option_P = false;
    public static boolean optionQ = false;
    public static boolean optionR = false;
    public static String optionS = null;
    public static boolean optionT = false;
    public static boolean optionW = false;
    public static boolean optionV = false;
    public static boolean option_V = true;

    // count paths in depgraphs?
    public static boolean countPaths = false;

    // the scanned entry file (canonical)
    public static File entryFile;

    // pixy's home directory (from environment)
    public static File pixy_home;

    // home directory of FSA Utils; can be null
    public static String fsa_home;

    // name of the config directory
    public static String configDir = "config";

    // List of Files specifying the include_path (given in php.ini, can be
    // checked with the PHP function "get_include_path()")
    public static List<File> includePaths;

    // path of the php binary (for simulating builtin functions);
    // can be null
    public static String phpBin;

    // directory where to dump graphs
    public static String graphPath;

    // indices of the $_SERVER ($HTTP_SERVER_VARS) array that cannot
    // be controlled by an attacker (and hence, are harmless)
    public static Set<String> harmlessServerIndices;

    // returns true if the given varname is $_SERVER[...]
    // or $HTTP_SERVER_VARS[...], with ... == one of the above indices
    public static boolean isHarmlessServerVar(String varName) {

        // extract index name
        String index;
        if (varName.startsWith("$_SERVER[") && varName.endsWith("]")) {
            index = varName.substring(9, varName.length() - 1);
        } else if (varName.startsWith("$HTTP_SERVER_VARS[") && varName.endsWith("]")) {
            index = varName.substring(18, varName.length() - 1);
        } else {
            return false;
        }

        // compare index name
        if (harmlessServerIndices.contains(index)) {
            return true;
        } else {
            return false;
        }
    }

    public static void addHarmlessServerIndex(String indexName) {
        harmlessServerIndices.add(indexName);
    }

    // builtin function models ****************************************

    public static void readModelFiles() {
        for (DepClientInfo dci : analyses) {
            FunctionModels fm = readModelFile(dci);
            dci.setFunctionModels(fm);
        }
    }

    // read models for builtin php functions
    private static FunctionModels readModelFile(DepClientInfo dci) {

        Set<String> f_evil = new HashSet<String>();
        Map<String, Set<Integer>> f_multi = new HashMap<String, Set<Integer>>();
        Map<String, Set<Integer>> f_invMulti = new HashMap<String, Set<Integer>>();
        Set<String> f_strongSanit = new HashSet<String>();
        Map<String, Set<Integer>> f_weakSanit = new HashMap<String, Set<Integer>>();

        String strongSanitMarker = "0";
        String weakSanitMarker = "1";
        String multiMarker = "2";
        String invMultiMarker = "3";
        String evilMarker = "4";

        // read file into properties object
        String modelFileName = MyOptions.pixy_home + "/" + MyOptions.configDir + "/model_" + dci.getName() + ".txt";
        File modelFile = new File(modelFileName);
        Properties sinkProps = new Properties();
        try {
            FileInputStream in = new FileInputStream(modelFile);
            sinkProps.load(in);
            in.close();
        } catch (FileNotFoundException e) {
            Utils.bail("Error: Can't find configuration file: " + modelFileName);
        } catch (IOException e) {
            Utils.bail("Error: I/O exception while reading configuration file:" + modelFileName,
                e.getMessage());
        }

        Class tacOps;
        try {
            tacOps = Class.forName("at.ac.tuwien.infosys.www.pixy.conversion.TacOperators");
        } catch (ClassNotFoundException e1) {
            throw new RuntimeException("SNH");
        }

        // convert properties...
        for (Map.Entry<Object, Object> propsEntry : sinkProps.entrySet()) {

            String funcName = ((String) propsEntry.getKey()).trim();
            String funcList = (String) propsEntry.getValue();

            if (!BuiltinFunctions.isBuiltinFunction(funcName)) {
                if (funcName.startsWith("op(") && funcName.endsWith(")")) {
                    funcName = funcName.substring(3, funcName.length() - 1);
                    // convert the operator name to its symbol
                    try {
                        Field field = tacOps.getDeclaredField(funcName);
                        funcName = TacOperators.opToName(field.getInt(null));
                    } catch (NoSuchFieldException e) {
                        Utils.bail("Error: Non-builtin function in config file: " + funcName);
                        continue;
                    } catch (IllegalAccessException e) {
                        Utils.bail("Error: Non-builtin function in config file: " + funcName);
                    }
                } else {
                    Utils.bail("Error: Non-builtin function in config file: " + funcName);
                }
            }

            StringTokenizer funcTokenizer = new StringTokenizer(funcList, ":");
            if (!funcTokenizer.hasMoreTokens()) {
                Utils.bail("Error: Missing type for builtin function model: " + funcName);
            }

            String type = funcTokenizer.nextToken().trim();
            if (type.equals(strongSanitMarker)) {

                // strong sanitization
                f_strongSanit.add(funcName);

            } else if (type.equals(weakSanitMarker)) {

                // weak sanitization
                Set<Integer> params = new HashSet<Integer>();
                while (funcTokenizer.hasMoreTokens()) {
                    String param = funcTokenizer.nextToken().trim();
                    try {
                        params.add(Integer.valueOf(param));
                    } catch (NumberFormatException e) {
                        Utils.bail("Error: Illegal parameter for builtin function model: " + funcName);
                    }
                }
                f_weakSanit.put(funcName, params);

            } else if (type.equals(multiMarker)) {

                // multi-dependency
                Set<Integer> params = new HashSet<Integer>();
                while (funcTokenizer.hasMoreTokens()) {
                    String param = funcTokenizer.nextToken().trim();
                    try {
                        params.add(Integer.valueOf(param));
                    } catch (NumberFormatException e) {
                        Utils.bail("Error: Illegal parameter for builtin function model: " + funcName);
                    }
                }
                f_multi.put(funcName, params);

            } else if (type.equals(invMultiMarker)) {

                // inverse multi-dependency
                Set<Integer> params = new HashSet<Integer>();
                while (funcTokenizer.hasMoreTokens()) {
                    String param = funcTokenizer.nextToken().trim();
                    try {
                        params.add(Integer.valueOf(param));
                    } catch (NumberFormatException e) {
                        Utils.bail("Error: Illegal parameter for builtin function model: " + funcName);
                    }
                }
                f_invMulti.put(funcName, params);

            } else if (type.equals(evilMarker)) {
                // evil functions
                f_evil.add(funcName);
            } else {
                Utils.bail("Error: Unknown type for builtin function model: " + funcName);
            }

        }

        // add Pixy's suppression function
        f_strongSanit.add(InternalStrings.suppression);

        return new FunctionModels(f_evil, f_multi, f_invMulti, f_strongSanit, f_weakSanit);
    }

    // custom, user-defined sinks *************************************

    public static boolean isSink(String functionName) {
        for (DepClientInfo dci : analyses) {
            if (dci.getSinks().containsKey(functionName)) {
                return true;
            }
        }
        return false;
    }

    // adds a sink (given by function name and sensitive indices) to the given sink map;
    // if indices == null, it means that ALL indices are sensitive (necessary for functions
    // with an arbitrary number of sensitive indices, e.g.: printf)
    public static void addSink(Map<String, Set<Integer>> sinkMap, String name, int... indices) {
        Set<Integer> indexSet;
        if (indices == null) {
            indexSet = null;
        } else {
            indexSet = new HashSet<Integer>();
            for (int index : indices) {
                indexSet.add(index);
            }
        }
        sinkMap.put(name, indexSet);
    }

    // reads the given sink file, fills the given map with the contained info,
    // and returns the contained "sinkType" property (can also be null)
    private static String readSinkFile(String sinkFileName,
                                       Map<String, Set<Integer>> sinks) {

        // read file into properties object
        File sinkFile = new File(sinkFileName);
        Properties sinkProps = new Properties();
        //System.out.println("Reading sink config file: " + sinkFileName);
        try {
            FileInputStream in = new FileInputStream(sinkFile);
            sinkProps.load(in);
            in.close();
        } catch (FileNotFoundException e) {
            System.out.println("Warning: Can't find sink configuration file: " + sinkFileName);
        } catch (IOException e) {
            System.out.println("Warning: I/O exception while reading configuration file:" + sinkFileName);
            System.out.println(e.getMessage());
        }

        // convert properties into the above map
        String sinkType = null;
        for (Map.Entry<Object, Object> propsEntry : sinkProps.entrySet()) {
            String functionName = (String) propsEntry.getKey();
            String params = (String) propsEntry.getValue();

            // the entry with this name is special: it denotes the analysis type
            // for which the sinks shall be adjusted
            if (functionName.equals("sinkType")) {
                sinkType = params;
                continue;
            }

            // convert parameter list for this sink
            StringTokenizer paramTokenizer = new StringTokenizer(params, ":");
            int numTokens = paramTokenizer.countTokens();
            Set<Integer> paramSet = new HashSet<Integer>();
            while (paramTokenizer.hasMoreTokens()) {
                String param = paramTokenizer.nextToken();
                try {
                    paramSet.add(Integer.parseInt(param));
                } catch (NumberFormatException e) {
                    System.out.println("Warning: Illegal param argument for " + functionName + " in " + sinkFileName);
                    System.out.println("param: " + param);
                }
            }
            if (numTokens == 0) {
                // e.g., for printf (XSS): all params are sensitive
                paramSet = null;
            }

            sinks.put(functionName, paramSet);
        }

        return sinkType;
    }

    // add builtin function sinks (represented by CfgNodeCallBuiltin)
    public static void initSinks() {
        for (DepClientInfo dci : analyses) {
            String sinkFileName = "sinks_" + dci.getName() + ".txt";
            Map<String, Set<Integer>> sinks = new HashMap<String, Set<Integer>>();
            readSinkFile(MyOptions.pixy_home + "/" + MyOptions.configDir + "/" + sinkFileName, sinks);
            dci.addSinks(sinks);
        }
    }

    // read user-defined custom sink files
    public static void readCustomSinkFiles() {

        if (MyOptions.optionS != null) {

            // "sink file name" tokenizer
            StringTokenizer sfnTokenizer = new StringTokenizer(MyOptions.optionS, ":");

            // for each given sink file...
            while (sfnTokenizer.hasMoreTokens()) {
                String sinkFileName = sfnTokenizer.nextToken();

                // fill this map with the information contained in the file
                Map<String, Set<Integer>> sinks = new HashMap<String, Set<Integer>>();

                String sinkType = readSinkFile(sinkFileName, sinks);

                if (sinkType == null) {
                    System.out.println("Missing sinkType in file " + sinkFileName);
                } else {
                    DepClientInfo dci = name2Analysis.get(sinkType);
                    if (dci == null) {
                        System.out.println("Invalid sinkType in file " + sinkFileName);
                        System.out.println("- " + sinkType);
                    } else {
                        dci.addSinks(sinks);
                    }
                }
            }
        }
    }

    private static DepClientInfo[] analyses = {
        new DepClientInfo("xss", "at.ac.tuwien.infosys.www.pixy.XSSAnalysis"),
        new DepClientInfo("sql", "at.ac.tuwien.infosys.www.pixy.SQLAnalysis"),
        new DepClientInfo("sqlsanit", "at.ac.tuwien.infosys.www.pixy.sanit.SQLSanitAnalysis"),
        new DepClientInfo("xsssanit", "at.ac.tuwien.infosys.www.pixy.sanit.XSSSanitAnalysis"),
        new DepClientInfo("file", "at.ac.tuwien.infosys.www.pixy.FileAnalysis")
    };

    // "name to depclientinfo" mapping
    private static Map<String, DepClientInfo> name2Analysis;

    // "class name to analysis name" mapping
    private static Map<String, String> className2Name;

    // flags requested analysis
    public static boolean setAnalyses(String taintStrings) {

        if (taintStrings == null) {
            // no analyses are requested, which is also OK
            return true;
        }

        StringTokenizer st = new StringTokenizer(taintStrings, ":");
        while (st.hasMoreTokens()) {

            String taintString = st.nextToken();

            DepClientInfo dci = name2Analysis.get(taintString);
            if (dci != null) {
                dci.setPerformMe(true);
            } else {
                System.out.println("Invalid analysis type: " + taintString + ".");
                System.out.println("Choose one of the following: ");
                StringBuilder b = new StringBuilder();
                for (String name : name2Analysis.keySet()) {
                    b.append(name + ", ");
                }
                if (!name2Analysis.isEmpty()) {
                    b.deleteCharAt(b.length() - 1);
                    b.deleteCharAt(b.length() - 1);
                }
                System.out.println(b.toString());
                return false;
            }
        }
        return true;
    }

//  ********************************************************************************

    // STATIC INITIALIZATIONS
    static {

        // pixy's home directory

        String home = System.getProperty("pixy.home");
        if (home == null) {
            Utils.bail("System property 'pixy.home' not set");
        }
        try {
            MyOptions.pixy_home = (new File(home)).getCanonicalFile();
        } catch (IOException e) {
            Utils.bail("can't set pixy_home");
        }

        // harmless server indices
        harmlessServerIndices = new HashSet<String>();

        // name2depclient mapping

        name2Analysis = new HashMap<String, DepClientInfo>();
        className2Name = new HashMap<String, String>();
        for (DepClientInfo dci : analyses) {
            name2Analysis.put(dci.getName(), dci);
            className2Name.put(dci.getClassName(), dci.getName());
        }

    }

    public static DepClientInfo getDepClientInfo(String analysisClassName) {
        String analysisName = className2Name.get(analysisClassName);
        if (analysisName == null) {
            throw new RuntimeException("Illegal analysis class: " + analysisClassName);
        }
        DepClientInfo dci = name2Analysis.get(analysisName);
        if (dci == null) {
            throw new RuntimeException("Illegal analysis name: " + analysisName);
        }
        return dci;

    }

    public static DepClientInfo[] getDepClients() {
        return analyses;
    }

    public static String getAnalysisNames() {
        StringBuilder b = new StringBuilder();
        for (String name : name2Analysis.keySet()) {
            b.append(name);
            b.append(", ");
        }
        if (b.length() > 2) {
            b.deleteCharAt(b.length() - 1);
            b.deleteCharAt(b.length() - 1);
        }
        return b.toString();
    }
}