import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

enum Priority {
    CRITICAL(1), HIGH(2), NORMAL(3);
    private final int rank;
    Priority(int rank) { this.rank = rank; }
    int rank() { return rank; }
    static Priority parse(String value) {
        if (value == null) return NORMAL;
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "critical": return CRITICAL;
            case "high": return HIGH;
            default: return NORMAL;
        }
    }
}

class Reservoir {
    private final String id;
    private int dailySupply;
    private int remainingSupply;
    Reservoir(String id, int supply) {
        if (id == null || id.trim().isEmpty() || supply < 0)
            throw new IllegalArgumentException("Invalid reservoir ID or supply.");
        this.id = id; dailySupply = supply; remainingSupply = supply;
    }
    String getId() { return id; }
    int getDailySupply() { return dailySupply; }
    int getRemainingSupply() { return remainingSupply; }
    void allocateWater(int amount) {
        if (amount < 0 || amount > remainingSupply)
            throw new IllegalArgumentException("Invalid allocation for " + id);
        remainingSupply -= amount;
    }
    void resetDailySupply() { remainingSupply = dailySupply; }
    void updateDailySupply(int supply) {
        if (supply < 0) throw new IllegalArgumentException("Supply cannot be negative.");
        dailySupply = supply; remainingSupply = supply;
    }
}

class DemandArea {
    private final String id, name;
    private int demand;
    private final Priority priority;
    private int allocated, unmet;
    DemandArea(String id, String name, int demand, String priority) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty() || demand < 0)
            throw new IllegalArgumentException("Invalid demand area data.");
        this.id = id; this.name = name; this.demand = demand;
        this.priority = Priority.parse(priority); allocated = 0; unmet = demand;
    }
    String getId() { return id; }
    String getName() { return name; }
    int getDemand() { return demand; }
    Priority getPriority() { return priority; }
    int getAllocated() { return allocated; }
    int getUnmet() { return unmet; }
    int allocationPriority() { return priority.rank(); }
    void updateDemand(int value) {
        if (value < 0) throw new IllegalArgumentException("Demand cannot be negative.");
        demand = value; resetAllocation();
    }
    void setAllocation(int value) {
        if (value < 0 || value > demand) throw new IllegalArgumentException("Invalid area allocation.");
        allocated = value; unmet = demand - value;
    }
    void resetAllocation() { allocated = 0; unmet = demand; }
}

class CriticalInstitution extends DemandArea {
    CriticalInstitution(String id, String name, int demand) { super(id, name, demand, "Critical"); }
    @Override int allocationPriority() { return Priority.CRITICAL.rank(); }
}

class ResidentialArea extends DemandArea {
    ResidentialArea(String id, String name, int demand, String priority) { super(id, name, demand, priority); }
    @Override int allocationPriority() { return getPriority().rank(); }
}

class Pipeline {
    private final String id, from, to;
    private boolean working;
    Pipeline(String id, String from, String to, boolean working) {
        this.id = Objects.requireNonNull(id); this.from = Objects.requireNonNull(from);
        this.to = Objects.requireNonNull(to); this.working = working;
    }
    String getId() { return id; }
    String getFrom() { return from; }
    String getTo() { return to; }
    boolean isWorking() { return working; }
    void markFailed() { working = false; }
    void markWorking() { working = true; }
    @Override public String toString() { return id + " (" + from + " <-> " + to + ")"; }
}

class WaterRequest {
    private final String id;
    private final DemandArea area;
    private final long sequence;
    WaterRequest(String id, DemandArea area, long sequence) {
        this.id = id; this.area = area; this.sequence = sequence;
    }
    String getId() { return id; }
    DemandArea getArea() { return area; }
    long getSequence() { return sequence; }
}

class MaintenanceRequest {
    private final String id;
    private final Pipeline pipeline;
    private final String description;
    private boolean completed;
    MaintenanceRequest(String id, Pipeline pipeline, String description) {
        this.id = id; this.pipeline = pipeline; this.description = description; completed = false;
    }
    String getId() { return id; }
    Pipeline getPipeline() { return pipeline; }
    boolean isCompleted() { return completed; }
    void complete() { completed = true; }
    @Override public String toString() {
        return id + " - " + pipeline + " - " + description + (completed ? " [COMPLETED]" : " [OPEN]");
    }
}

class WaterNetwork {
    private final Map<String, List<String>> graph = new HashMap<>();
    // Composition: the network keeps references to its reservoirs, areas and pipelines.
    private final List<Reservoir> reservoirs = new ArrayList<>();
    private final List<DemandArea> demandAreas = new ArrayList<>();
    private final List<Pipeline> pipelines = new ArrayList<>();

    void addReservoir(Reservoir reservoir) { reservoirs.add(Objects.requireNonNull(reservoir)); }
    void addDemandArea(DemandArea area) { demandAreas.add(Objects.requireNonNull(area)); }
    void addPipeline(Pipeline p) { pipelines.add(Objects.requireNonNull(p)); rebuildGraph(); }
    List<Reservoir> getReservoirs() { return Collections.unmodifiableList(reservoirs); }
    List<DemandArea> getDemandAreas() { return Collections.unmodifiableList(demandAreas); }
    List<Pipeline> getPipelines() { return Collections.unmodifiableList(pipelines); }
    Pipeline findPipeline(String id) {
        for (Pipeline p : pipelines) if (p.getId().equalsIgnoreCase(id)) return p;
        return null;
    }
    void rebuildGraph() {
        graph.clear();
        for (Pipeline p : pipelines) {
            graph.computeIfAbsent(p.getFrom(), k -> new ArrayList<>());
            graph.computeIfAbsent(p.getTo(), k -> new ArrayList<>());
            if (p.isWorking()) {
                graph.get(p.getFrom()).add(p.getTo());
                graph.get(p.getTo()).add(p.getFrom());
            }
        }
    }
    Set<String> findReachableAreas(String start) {
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        visited.add(start);
        queue.offer(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : graph.getOrDefault(current, Collections.emptyList())) {
                if (visited.add(next)) queue.offer(next);
            }
        }
        return visited;
    }

    // BFS returns one shortest usable path through currently working pipelines.
    List<String> findPath(String start, String target) {
        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        queue.offer(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(target)) break;
            for (String next : graph.getOrDefault(current, Collections.emptyList())) {
                if (visited.add(next)) {
                    parent.put(next, current);
                    queue.offer(next);
                }
            }
        }
        if (!visited.contains(target)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String node = target; node != null; node = parent.get(node)) {
            result.add(node);
            if (node.equals(start)) break;
        }
        Collections.reverse(result);
        return result;
    }
}

abstract class WaterAllocationPolicy {
    abstract void allocate(List<DemandArea> areas, List<Reservoir> reservoirs,
                          WaterNetwork network, List<String> eventLog);
}

class PriorityBasedAllocationPolicy extends WaterAllocationPolicy {
    private long nextRequestSequence = 1L;

    private int takeFromArea(DemandArea area, int requested, List<Reservoir> reservoirs,
                             Map<String, Set<String>> reach) {
        int left = Math.max(0, requested), allocated = 0;
        List<Reservoir> candidates = new ArrayList<>();
        for (Reservoir reservoir : reservoirs) {
            if (reach.getOrDefault(reservoir.getId(), Collections.emptySet()).contains(area.getId())
                    && reservoir.getRemainingSupply() > 0) candidates.add(reservoir);
        }
        candidates.sort(Comparator.comparingInt(Reservoir::getRemainingSupply).reversed()
                .thenComparing(Reservoir::getId));
        for (Reservoir reservoir : candidates) {
            if (left <= 0) break;
            int part = Math.min(left, reservoir.getRemainingSupply());
            reservoir.allocateWater(part);
            left -= part;
            allocated += part;
        }
        return allocated;
    }

    // If a connected reservoir group cannot cover all 40% minimums,
    // divide its available supply proportionally among its reachable areas.
    private void allocateMinimums(List<DemandArea> areas, List<Reservoir> reservoirs,
                                  Map<String, Set<String>> reach) {
        Map<String, List<DemandArea>> groups = new LinkedHashMap<>();
        Map<String, List<Reservoir>> groupReservoirs = new LinkedHashMap<>();

        for (DemandArea area : areas) {
            List<String> sourceIds = new ArrayList<>();
            for (Reservoir reservoir : reservoirs) {
                if (reach.getOrDefault(reservoir.getId(), Collections.emptySet()).contains(area.getId()))
                    sourceIds.add(reservoir.getId());
            }
            if (sourceIds.isEmpty()) continue;
            Collections.sort(sourceIds);
            String key = String.join("|", sourceIds);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(area);
            List<Reservoir> sources = new ArrayList<>();
            for (String id : sourceIds) {
                for (Reservoir reservoir : reservoirs)
                    if (reservoir.getId().equals(id)) sources.add(reservoir);
            }
            groupReservoirs.putIfAbsent(key, sources);
        }

        for (String key : groups.keySet()) {
            List<DemandArea> group = groups.get(key);
            List<Reservoir> sources = groupReservoirs.get(key);
            int minimumTotal = 0;
            int supply = sources.stream().mapToInt(Reservoir::getRemainingSupply).sum();
            for (DemandArea area : group)
                minimumTotal += (int) Math.floor(area.getDemand() * 0.40);

            int remainingPool = Math.min(minimumTotal, supply);
            Map<DemandArea, Integer> targets = new LinkedHashMap<>();
            int assignedTargets = 0;
            for (DemandArea area : group) {
                int minimum = (int) Math.floor(area.getDemand() * 0.40);
                int target = minimumTotal <= supply || minimumTotal == 0
                        ? minimum : (int) Math.floor((double) minimum * supply / minimumTotal);
                targets.put(area, target);
                assignedTargets += target;
            }
            // Distribute rounding litres in stable order without exceeding 40% targets.
            int roundingLeft = remainingPool - assignedTargets;
            for (DemandArea area : group) {
                if (roundingLeft <= 0) break;
                int cap = (int) Math.floor(area.getDemand() * 0.40);
                int current = targets.get(area);
                if (current < cap) {
                    targets.put(area, current + 1);
                    roundingLeft--;
                }
            }
            for (DemandArea area : group) {
                int got = takeFromArea(area, targets.get(area), reservoirs, reach);
                if (got > 0) area.setAllocation(got);
            }
        }
    }

    // Share remaining water proportionally among areas of the same importance.
    private void allocatePriorityFairly(Priority priority, List<DemandArea> areas,
                                        List<Reservoir> reservoirs,
                                        Map<String, Set<String>> reach) {
        for (int round = 0; round < 100; round++) {
            // Rebuild the queue at the start of each round because unmet demand changes
            // after allocation. Priority and arrival sequence provide deterministic ties.
            // No queued request is mutated; changed requests are represented by fresh records.
            PriorityQueue<WaterRequest> requestQueue = new PriorityQueue<>(
                    Comparator.comparingInt((WaterRequest request) ->
                                    request.getArea().allocationPriority())
                            .thenComparingLong(WaterRequest::getSequence));
            Map<String, List<String>> areaSourceMap = new HashMap<>();
            for (DemandArea area : areas) {
                List<String> areaSources = new ArrayList<>();
                for (Reservoir reservoir : reservoirs) {
                    if (reach.getOrDefault(reservoir.getId(), Collections.emptySet()).contains(area.getId()))
                        areaSources.add(reservoir.getId());
                }
                if (!areaSources.isEmpty() && area.allocationPriority() == priority.rank()
                        && area.getUnmet() > 0) {
                    requestQueue.offer(new WaterRequest("WR-" + nextRequestSequence,
                            area, nextRequestSequence++));
                    areaSourceMap.put(area.getId(), areaSources);
                }
            }

            List<DemandArea> candidates = new ArrayList<>();
            Set<String> sourceIds = new LinkedHashSet<>();
            int totalUnmet = 0;
            while (!requestQueue.isEmpty()) {
                WaterRequest request = requestQueue.poll();
                DemandArea area = request.getArea();
                candidates.add(area);
                sourceIds.addAll(areaSourceMap.getOrDefault(area.getId(), Collections.emptyList()));
                totalUnmet += area.getUnmet();
            }
            if (candidates.isEmpty() || totalUnmet <= 0) return;

            int pool = 0;
            for (Reservoir reservoir : reservoirs)
                if (sourceIds.contains(reservoir.getId())) pool += reservoir.getRemainingSupply();
            if (pool <= 0) return;

            int before = reservoirs.stream().mapToInt(Reservoir::getRemainingSupply).sum();
            Map<DemandArea, Integer> requests = new LinkedHashMap<>();
            int requestedTotal = 0;
            for (DemandArea area : candidates) {
                int share = (int) Math.floor((double) pool * area.getUnmet() / totalUnmet);
                if (share == 0) share = 1;
                share = Math.min(area.getUnmet(), share);
                requests.put(area, share);
                requestedTotal += share;
            }
            int over = Math.max(0, requestedTotal - pool);
            List<DemandArea> reverse = new ArrayList<>(candidates);
            Collections.reverse(reverse);
            for (DemandArea area : reverse) {
                if (over <= 0) break;
                int current = requests.getOrDefault(area, 0);
                int cut = Math.min(over, current);
                requests.put(area, current - cut);
                over -= cut;
            }
            for (DemandArea area : candidates) {
                int requested = requests.getOrDefault(area, 0);
                if (requested <= 0) continue;
                int got = takeFromArea(area, requested, reservoirs, reach);
                if (got > 0) area.setAllocation(area.getAllocated() + got);
            }
            int after = reservoirs.stream().mapToInt(Reservoir::getRemainingSupply).sum();
            if (after >= before) return;
        }
    }

    @Override void allocate(List<DemandArea> areas, List<Reservoir> reservoirs,
                            WaterNetwork network, List<String> log) {
        // Recalculate from original daily supply after topology or demand changes.
        for (Reservoir reservoir : reservoirs) reservoir.resetDailySupply();
        for (DemandArea area : areas) area.resetAllocation();

        Map<String, Set<String>> reach = new HashMap<>();
        for (Reservoir reservoir : reservoirs)
            reach.put(reservoir.getId(), network.findReachableAreas(reservoir.getId()));

        // Phase 1: every reachable area gets its 40% minimum before priority allocation.
        allocateMinimums(areas, reservoirs, reach);

        // Phase 2: distribute remaining supply Critical, then High, then Normal.
        allocatePriorityFairly(Priority.CRITICAL, areas, reservoirs, reach);
        allocatePriorityFairly(Priority.HIGH, areas, reservoirs, reach);
        allocatePriorityFairly(Priority.NORMAL, areas, reservoirs, reach);

        // HashMap snapshots provide direct ID-based access to the final daily balances.
        Map<String, Integer> demandByArea = new HashMap<>();
        Map<String, Integer> allocatedByArea = new HashMap<>();
        Map<String, Integer> unmetByArea = new HashMap<>();
        Map<String, Integer> remainingByReservoir = new HashMap<>();
        for (DemandArea area : areas) {
            demandByArea.put(area.getId(), area.getDemand());
            allocatedByArea.put(area.getId(), area.getAllocated());
            unmetByArea.put(area.getId(), area.getUnmet());
        }
        for (Reservoir reservoir : reservoirs)
            remainingByReservoir.put(reservoir.getId(), reservoir.getRemainingSupply());

        System.out.println("\n================ WATER ALLOCATION ================");
        System.out.printf("%-5s %-22s %-9s %-10s %-10s %-10s%n",
                "ID", "AREA", "PRIORITY", "DEMAND(L)", "ALLOC(L)", "UNMET(L)");
        System.out.println("--------------------------------------------------------------------------");
        for (DemandArea area : areas) {
            System.out.printf("%-5s %-22s %-9s %-10d %-10d %-10d%n", area.getId(), area.getName(),
                    area.getPriority(), demandByArea.get(area.getId()),
                    allocatedByArea.get(area.getId()), unmetByArea.get(area.getId()));
            if (area.getAllocated() > 0)
                log.add(now() + " Allocation: " + area.getName() + " received " + area.getAllocated() + " L.");
            if (area.getUnmet() > 0)
                log.add(now() + " Unmet demand: " + area.getName() + " has " + area.getUnmet() + " L unmet.");
        }
        System.out.println("\nRemaining reservoir supply:");
        for (Reservoir reservoir : reservoirs)
            System.out.println(reservoir.getId() + ": " + remainingByReservoir.get(reservoir.getId())
                    + " / " + reservoir.getDailySupply() + " L");
        System.out.println("Policy: 40% minimum for every reachable area first; then remaining water: Critical -> High -> Normal.");
        System.out.println("Areas within the same priority share available water proportionally to unmet demand.");
        System.out.println("BFS indicates connectivity only; it does not calculate hydraulic pressure or flow.");
    }
    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

public class SmartWaterDistribution {
    private static final Scanner IN = new Scanner(System.in);
    private static final List<String> EVENTS = new ArrayList<>();
    private static final Deque<MaintenanceRequest> MAINTENANCE_QUEUE = new ArrayDeque<>();
    private static final List<MaintenanceRequest> MAINTENANCE_HISTORY = new ArrayList<>();
    private static int MANUAL_DEMAND_FAULT_EVENT_COUNT = 0;
    private static String now() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
    private static void log(String s) { EVENTS.add(now() + " " + s); }

    private static void showAreas(List<DemandArea> areas) {
        System.out.println("\nID    AREA                   PRIORITY   DEMAND(L)");
        for (DemandArea a : areas)
            System.out.printf("%-5s %-22s %-10s %d%n", a.getId(), a.getName(), a.getPriority(), a.getDemand());
    }
    private static void showPipelines(WaterNetwork net) {
        System.out.println("\nPIPELINE STATUS");
        for (Pipeline p : net.getPipelines())
            System.out.println(p + " -> " + (p.isWorking() ? "WORKING" : "FAILED"));
    }
    private static void showConnectivity(List<Reservoir> reservoirs, List<DemandArea> areas, WaterNetwork net) {
        Map<String, Set<String>> reach = new HashMap<>();
        for (Reservoir r : reservoirs) reach.put(r.getId(), net.findReachableAreas(r.getId()));
        System.out.println("\nCONNECTIVITY STATUS");
        for (DemandArea a : areas) {
            List<String> sources = new ArrayList<>();
            for (Reservoir r : reservoirs) {
                if (reach.get(r.getId()).contains(a.getId())) sources.add(r.getId());
            }
            if (sources.isEmpty()) {
                System.out.println(a.getId() + " - " + a.getName() + ": DISCONNECTED");
            } else {
                System.out.println(a.getId() + " - " + a.getName() + ": REACHABLE from " + String.join(", ", sources));
                for (String source : sources) {
                    List<String> path = net.findPath(source, a.getId());
                    System.out.println("      BFS path from " + source + ": " + String.join(" -> ", path));
                }
            }
        }
    }

    private static void failPipeline(WaterNetwork net, List<Reservoir> reservoirs,
                                     List<DemandArea> areas, WaterAllocationPolicy policy) {
        showPipelines(net); System.out.print("Enter pipeline ID to fail (e.g. P1): ");
        Pipeline p = net.findPipeline(IN.nextLine().trim());
        if (p == null) { System.out.println("Pipeline not found."); return; }
        if (!p.isWorking()) { System.out.println("Pipeline is already failed."); return; }
        p.markFailed(); net.rebuildGraph();
        MANUAL_DEMAND_FAULT_EVENT_COUNT++;
        String id = "MR-" + (MAINTENANCE_HISTORY.size() + 1);
        MaintenanceRequest request = new MaintenanceRequest(id, p, "Repair failed pipeline " + p.getId());
        MAINTENANCE_QUEUE.offerLast(request);
        MAINTENANCE_HISTORY.add(request);
        log("FAULT: " + p.getId() + " failed; maintenance request " + id + " opened.");
        showConnectivity(reservoirs, areas, net); policy.allocate(areas, reservoirs, net, EVENTS);
    }
    private static void repairPipeline(WaterNetwork net, List<Reservoir> reservoirs,
                                       List<DemandArea> areas, WaterAllocationPolicy policy) {
        if (MAINTENANCE_QUEUE.isEmpty()) {
            System.out.println("No open maintenance requests.");
            return;
        }
        System.out.println("Next request in FIFO queue: " + MAINTENANCE_QUEUE.peekFirst());
        System.out.print("Process next maintenance request? (Y/N): ");
        String answer = IN.nextLine().trim();
        if (!answer.equalsIgnoreCase("Y")) return;

        MaintenanceRequest request = MAINTENANCE_QUEUE.pollFirst();
        Pipeline pipeline = request.getPipeline();
        pipeline.markWorking();
        request.complete();
        net.rebuildGraph();
        log("REPAIR: " + pipeline.getId() + " repaired; " + request.getId() + " completed from FIFO queue.");
        showConnectivity(reservoirs, areas, net);
        policy.allocate(areas, reservoirs, net, EVENTS);
    }
    private static void updateDemand(List<DemandArea> areas, List<Reservoir> reservoirs,
                                     WaterNetwork net, WaterAllocationPolicy policy) {
        System.out.print("Enter area ID (A1-A10): "); String id = IN.nextLine().trim();
        DemandArea selected = null;
        for (DemandArea a : areas) if (a.getId().equalsIgnoreCase(id)) selected = a;
        if (selected == null) { System.out.println("Area not found."); return; }
        System.out.print("Enter new demand in litres: ");
        try {
            int value = Integer.parseInt(IN.nextLine().trim());
            if (value < 0) { System.out.println("Demand cannot be negative."); return; }
            int old = selected.getDemand(); selected.updateDemand(value);
            MANUAL_DEMAND_FAULT_EVENT_COUNT++;
            log("DEMAND UPDATE: " + selected.getName() + " changed from " + old + " L to " + value + " L.");
            policy.allocate(areas, reservoirs, net, EVENTS);
        } catch (NumberFormatException ex) { System.out.println("Enter a whole number."); }
    }
    private static void updateReservoirSupply(List<Reservoir> reservoirs, List<DemandArea> areas,
                                              WaterNetwork net, WaterAllocationPolicy policy) {
        System.out.print("Enter reservoir ID (R1/R2): ");
        String id = IN.nextLine().trim();
        Reservoir selected = null;
        for (Reservoir r : reservoirs) if (r.getId().equalsIgnoreCase(id)) selected = r;
        if (selected == null) { System.out.println("Reservoir not found."); return; }
        System.out.print("Enter new daily supply in litres: ");
        try {
            int value = Integer.parseInt(IN.nextLine().trim());
            if (value < 0) { System.out.println("Supply cannot be negative."); return; }
            int old = selected.getDailySupply();
            selected.updateDailySupply(value);
            log("SUPPLY UPDATE: " + selected.getId() + " daily supply changed from " + old + " L to " + value + " L.");
            policy.allocate(areas, reservoirs, net, EVENTS);
        } catch (NumberFormatException ex) { System.out.println("Enter a whole number."); }
    }

    private static void recordManualEvent(WaterNetwork net, List<Reservoir> reservoirs,
                                           List<DemandArea> areas, WaterAllocationPolicy policy) {
        System.out.println("\n===== MANUAL EVENT ENTRY =====");
        System.out.println("1. Enter a demand reading");
        System.out.println("2. Record a pipeline fault");
        System.out.print("Choose event type: ");
        String choice = IN.nextLine().trim();

        switch (choice) {
            case "1":
                updateDemand(areas, reservoirs, net, policy);
                break;
            case "2":
                failPipeline(net, reservoirs, areas, policy);
                break;
            default:
                System.out.println("Invalid choice. Choose 1 or 2.");
        }
    }

    private static void showEvents() {
        System.out.println("\nEVENT HISTORY");
        if (EVENTS.isEmpty()) System.out.println("No events recorded.");
        for (int i = 0; i < EVENTS.size(); i++) System.out.println((i + 1) + ". " + EVENTS.get(i));
        System.out.println("Total recorded log entries: " + EVENTS.size());
        System.out.println("Manual demand/fault events: " + MANUAL_DEMAND_FAULT_EVENT_COUNT
                + " / 15 required for the case-study demonstration.");
    }
    private static void showMaintenance() {
        System.out.println("\nMAINTENANCE REQUESTS");
        if (MAINTENANCE_HISTORY.isEmpty()) System.out.println("No maintenance requests.");
        for (MaintenanceRequest m : MAINTENANCE_HISTORY) System.out.println(m);
        System.out.println("Open FIFO queue size: " + MAINTENANCE_QUEUE.size());
    }

    public static void main(String[] args) {
        List<Reservoir> reservoirs = new ArrayList<>();
        reservoirs.add(new Reservoir("R1", 10000));
        reservoirs.add(new Reservoir("R2", 8000));
        List<DemandArea> areas = new ArrayList<>();
        areas.add(new CriticalInstitution("A1", "Hospital", 5000));
        areas.add(new DemandArea("A2", "School", 3000, "High"));
        areas.add(new ResidentialArea("A3", "Residential East", 4000, "Normal"));
        areas.add(new ResidentialArea("A4", "Market Area", 2000, "Normal"));
        areas.add(new ResidentialArea("A5", "Residential West", 2500, "Normal"));
        areas.add(new ResidentialArea("A6", "Industrial Area", 3500, "High"));
        areas.add(new CriticalInstitution("A7", "Fire Station", 1500));
        areas.add(new ResidentialArea("A8", "Community Centre", 1800, "High"));
        areas.add(new ResidentialArea("A9", "Downtown Zone", 3200, "High"));
        areas.add(new ResidentialArea("A10", "Agricultural Zone", 2800, "Normal"));

        WaterNetwork net = new WaterNetwork();
        for (Reservoir reservoir : reservoirs) net.addReservoir(reservoir);
        for (DemandArea area : areas) net.addDemandArea(area);
        // 15 links. Pipelines are modelled as bidirectional; capacity is assumed adequate.
        net.addPipeline(new Pipeline("P1", "R1", "A1", true));
        net.addPipeline(new Pipeline("P2", "A1", "A2", true));
        net.addPipeline(new Pipeline("P3", "A2", "A3", true));
        net.addPipeline(new Pipeline("P4", "A3", "A4", true));
        net.addPipeline(new Pipeline("P5", "A2", "A5", true));
        net.addPipeline(new Pipeline("P6", "A5", "A6", true));
        net.addPipeline(new Pipeline("P7", "A3", "A5", true));
        net.addPipeline(new Pipeline("P8", "A4", "A6", true));
        net.addPipeline(new Pipeline("P9", "A1", "A3", true));
        net.addPipeline(new Pipeline("P10", "A2", "A6", true));
        net.addPipeline(new Pipeline("P11", "A4", "A5", true));
        net.addPipeline(new Pipeline("P12", "R2", "A7", true));
        net.addPipeline(new Pipeline("P13", "A7", "A8", true));
        net.addPipeline(new Pipeline("P14", "R1", "A9", true));
        net.addPipeline(new Pipeline("P15", "A9", "A10", true));

        // Initial setup log: 10 demand areas + 2 reservoirs + 15 pipelines = 27 setup entries.
        for (DemandArea a : areas) log("DEMAND REGISTERED: " + a.getName() + ", " + a.getDemand() + " L, priority " + a.getPriority());
        for (Reservoir r : reservoirs) log("RESERVOIR INITIALIZED: " + r.getId() + " supply " + r.getDailySupply() + " L");
        for (Pipeline p : net.getPipelines()) log("PIPELINE INITIALIZED: " + p.getId() + " " + p.getFrom() + " <-> " + p.getTo() + " status WORKING");
        // Demand and fault history is entered manually by the operator through menu option 11.

        WaterAllocationPolicy policy = new PriorityBasedAllocationPolicy();
        System.out.println("====================================================");
        System.out.println("       SMART WATER DISTRIBUTION MANAGEMENT");
        System.out.println("====================================================");
        System.out.println("Town: Neer Nagar");
        System.out.println("Reservoirs: 2 | Demand areas: 10 | Pipeline links: 15");
        System.out.println("Model assumption: adequate pipeline capacity; pressure, leakage physics and pump control are excluded.");
        System.out.println("Initial setup events recorded: " + EVENTS.size());
        showAreas(areas); showConnectivity(reservoirs, areas, net);
        policy.allocate(areas, reservoirs, net, EVENTS);

        boolean running = true;
        while (running) {
            System.out.println("\n================ MAIN MENU ================");
            System.out.println("1. View demand areas");
            System.out.println("2. View connectivity");
            System.out.println("3. View pipeline status");
            System.out.println("4. Allocate / recalculate water");
            System.out.println("5. Record pipeline failure");
            System.out.println("6. Repair pipeline");
            System.out.println("7. Update area demand");
            System.out.println("8. View maintenance requests");
            System.out.println("9. View event history");
            System.out.println("10. Update reservoir daily supply");
            System.out.println("11. Enter manual demand/fault event");
            System.out.println("0. Exit");
            System.out.print("Choose an option: ");
            if (!IN.hasNextLine()) {
    System.out.println("Input ended. Exiting program.");
    break;
}


            switch (IN.nextLine().trim()) {
                case "1": showAreas(areas); break;
                case "2": showConnectivity(reservoirs, areas, net); break;
                case "3": showPipelines(net); break;
                case "4": log("ALLOCATION RECALCULATED by operator."); policy.allocate(areas, reservoirs, net, EVENTS); break;
                case "5": failPipeline(net, reservoirs, areas, policy); break;
                case "6": repairPipeline(net, reservoirs, areas, policy); break;
                case "7": updateDemand(areas, reservoirs, net, policy); break;
                case "8": showMaintenance(); break;
                case "9": showEvents(); break;
                case "10": updateReservoirSupply(reservoirs, areas, net, policy); break;
                case "11": recordManualEvent(net, reservoirs, areas, policy); break;
                case "0": running = false; System.out.println("Goodbye!"); break;
                default: System.out.println("Invalid option. Choose 0-11.");
            }
        }
        IN.close();
    }
}