import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.*;

public class Main {
    public static void main(String[] args) throws IOException {
        File path = new File(".");
        String[] fileList = path.list((d,n) -> n.endsWith(".txt"));
        if(fileList == null){
            System.out.println("No .txt file found. Fix that, now.");
            return;
        }

        List<String> files = Arrays.stream(fileList).sorted().toList();
        for(String inputFile : files){
            System.out.println("===" + inputFile + "===");

            List<String> lines = Files.readAllLines(new File(inputFile).toPath());

            CFGFunction cfg = CFGCrunch.parse(lines);
            CFGCrunch.killE(cfg);
            CFGCrunch.killUseless(cfg);
            CFGCrunch.print(cfg);
        }
    }

    public static class CFGFunction {
        public String start;
        public Map<String, List<List<String>>> rules;

        public CFGFunction(String start){
            this.start = start;
            this.rules = new LinkedHashMap<>();
        }

        public void addRule(String left, List<String> right){
            rules.computeIfAbsent(left, k -> new ArrayList<>()).add(right);
        }

        public Map<String, List<List<String>>> getRules(){ return rules; }
        public String getStart(){ return start; }
    }

    public static class CFGCrunch {

        public static CFGFunction parse(List<String> lines){
            String start = lines.get(0).split("-")[0].trim();
            CFGFunction cfg = new CFGFunction(start);

            for(String line: lines){
                String[] parts = line.split("-");
                String left = parts[0].trim();
                String[] rightParts = parts[1].split("\\|");

                for(String right : rightParts){
                    if(right.equals("0")){
                        cfg.addRule(left, List.of("ε"));        //testing if the actual real epsilon character works
                    } else {
                        List<String> symbols = new ArrayList<>();
                        for(char character : right.toCharArray()) symbols.add(String.valueOf(character));
                        cfg.addRule(left, symbols);
                    }
                }
            }
            return cfg;
        }

        public static void killE(CFGFunction cfg){
            Map<String, List<List<String>>> rules = cfg.getRules();
            Set<String> nothing = new HashSet<>();
            boolean changed = true;

            while(changed){
                changed = false;
                for(Map.Entry<String, List<List<String>>> entry : rules.entrySet()){
                    String left = entry.getKey();
                    for(List<String> right: entry.getValue()){
                        if((right.size() == 1 && right.get(0).equals("ε"))|| right.stream().allMatch(nothing::contains)){
                            if(nothing.add(left)) changed = true;
                        }
                    }
                }
            }

            Map<String, List<List<String>>> newRules = new LinkedHashMap<>();
            for (Map.Entry<String, List<List<String>>> entry: rules.entrySet()){
                String left = entry.getKey();
                for (List<String> right : entry.getValue()){
                    if (right.size() == 1 && right.get(0).equals("ε")) continue;

                    List<Integer> nullPosition = new ArrayList<>();
                    for(int innie=0; innie<right.size(); innie++){
                        if (nothing.contains(right.get(innie))) nullPosition.add(innie);
                    }

                    int pick = 1 << nullPosition.size();
                    for(int something=0; something < pick; something++){
                        List<String> newRight = new ArrayList<>();
                        for(int innie=0; innie < right.size(); innie++){
                            int index = nullPosition.indexOf(innie);
                            if (index != -1 && ((something >> index) & 1) == 1) continue;
                            newRight.add(right.get(innie));
                        }
                        if(!newRight.isEmpty()) newRules.computeIfAbsent(left, k -> new ArrayList<>()).add(newRight);
                    }
                }
            }
            rules.clear();
            rules.putAll(newRules);
        }

        public static void killUseless(CFGFunction cfg){
            Map<String, List<List<String>>> rules = cfg.getRules();
            String start = cfg.getStart();

            Set<String> generating = new HashSet<>();
            boolean changed = true;
            while(changed){
                changed = false;
                for(Map.Entry<String, List<List<String>>> entry : rules.entrySet()){
                    String left = entry.getKey();
                    for(List<String> right : entry.getValue()){
                        boolean allGenerate = true;
                        for(String symbol : right){
                            if(rules.containsKey(symbol) && !generating.contains(symbol)){
                                allGenerate = false;
                                break;
                            }
                        }
                        if(allGenerate && generating.add(left)) changed = true;
                    }
                }
            }

            rules.entrySet().removeIf(e -> !generating.contains(e.getKey()));
            for(Map.Entry<String, List<List<String>>> entry : rules.entrySet()){
                entry.getValue().removeIf(right -> right.stream().anyMatch(s -> rules.containsKey(s) && !generating.contains(s)));
            }

            Set<String> touchable = new HashSet<>();
            touchable.add(start);
            changed = true;
            while(changed){
                changed = false;
                for(String left : new ArrayList<>(touchable)){
                    for(List<String> right : rules.getOrDefault(left, List.of())){
                        for(String symbol : right){
                            if(rules.containsKey(symbol) && touchable.add(symbol)) changed = true;
                        }
                    }
                }
            }

            rules.entrySet().removeIf(e -> !touchable.contains(e.getKey()));
        }

        public static void print(CFGFunction cfg){
            for(Map.Entry<String, List<List<String>>> entry : cfg.getRules().entrySet()){
                String rightStr = entry.getValue().stream()
                        .map(right -> String.join("", right))
                        .collect(Collectors.joining("|"));
                System.out.println(entry.getKey() + "-" + rightStr);
            }
        }
    }
}
