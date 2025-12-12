import java.io.*;
import java.util.*;
import java.util.regex.*;

public class ConfigParser {
    // Regex for tuples like (c,1) or (1,c)
    private static final Pattern TUPLE_PATTERN = Pattern.compile("\\(\\s*([a-zA-Z0-9]+)\\s*,\\s*([a-zA-Z0-9]+)\\s*\\)");

    public static Config parse(String filePath) throws IOException {
        Config config = new Config();
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line;
        
        boolean globalParamsFound = false;
        int nodesFound = 0;
        int neighborsFound = 0;
        
        while ((line = reader.readLine()) != null) {
            // Strip comments and trim
            int commentIndex = line.indexOf("#");
            if (commentIndex != -1) line = line.substring(0, commentIndex);
            line = line.trim();
            if (line.isEmpty()) continue;

            // Phase 1: Global Parameters
            if (!globalParamsFound) {
                if (!Character.isDigit(line.charAt(0))) continue;
                String[] tokens = line.split("\\s+");
                config.numNodes = Integer.parseInt(tokens[0]);
                config.minDelay = Integer.parseInt(tokens[1]);
                globalParamsFound = true;
                continue;
            }

            // Phase 2: Node Definitions
            if (nodesFound < config.numNodes) {
                if (!Character.isDigit(line.charAt(0))) continue;
                String[] tokens = line.split("\\s+");
                int id = Integer.parseInt(tokens[0]);
                String host = tokens[1];
                int port = Integer.parseInt(tokens[2]);
                config.nodes.put(id, new Config.NodeInfo(id, host, port));
                nodesFound++;
                continue;
            }

            // Phase 3: Neighbors
            // The kth valid line after node definitions contains neighbors for node k
            if (neighborsFound < config.numNodes) {
                if (!Character.isDigit(line.charAt(0))) continue;
                String[] tokens = line.split("\\s+");
                List<Integer> list = new ArrayList<>();
                // ALL tokens are neighbor IDs (no host ID prefix)
                for (int i = 0; i < tokens.length; i++) {
                    list.add(Integer.parseInt(tokens[i]));
                }
                config.neighbors.put(neighborsFound, list);  // Use index as node ID
                neighborsFound++;
                continue;
            }

            // Phase 4: Operations
            Matcher m = TUPLE_PATTERN.matcher(line);
            if (m.find()) {
                String t1 = m.group(1);
                String t2 = m.group(2);
                int id = -1; 
                String type = "";
                // Handle (1, c) or (c, 1) ambiguity
                if (isInt(t1)) { id = Integer.parseInt(t1); type = t2; }
                else if (isInt(t2)) { id = Integer.parseInt(t2); type = t1; }
                
                if (id != -1) config.operations.add(new Config.Operation(id, type));
            }
        }
        reader.close();
        return config;
    }

    private static boolean isInt(String s) {
        try { Integer.parseInt(s); return true; } catch(Exception e) { return false; }
    }
}