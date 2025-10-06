package AS33;

import java.util.*;

//------------------ Resource Classes ------------------
abstract class Resource {
 String name;
 int available;
 double costPerHour;

 public Resource(String name, int available, double costPerHour) {
     this.name = name;
     this.available = available;
     this.costPerHour = costPerHour;
 }

 public double getCost(double hours) {
     return available * costPerHour * hours;
 }
}

class Machine extends Resource {
 double powerKW;
 double energyCostPerKWh;

 public Machine(String name, int available, double costPerHour, double powerKW, double energyCostPerKWh) {
     super(name, available, costPerHour);
     this.powerKW = powerKW;
     this.energyCostPerKWh = energyCostPerKWh;
 }

 public double getEnergyCost(double hours) {
     return available * powerKW * hours * energyCostPerKWh;
 }
}

class Human extends Resource {
 double wagePerHour;

 public Human(String name, int available, double wagePerHour) {
     super(name, available, wagePerHour);
     this.wagePerHour = wagePerHour;
 }

 @Override
 public double getCost(double hours) {
     return available * wagePerHour * hours;
 }
}

class Material extends Resource {
 double unitCost;

 public Material(String name, int available, double unitCost) {
     super(name, available, 0);
     this.unitCost = unitCost;
 }

 public double getCostForUnits(int units) {
     return units * unitCost;
 }
}

class AGV extends Resource {
 double speedMetersPerSec;
 double distanceMeters;
 double loadTimeMin;
 double unloadTimeMin;
 double energyKWhPerKm;

 public AGV(String name, int available, double costPerHour,
            double speedMetersPerSec, double distanceMeters,
            double loadTimeMin, double unloadTimeMin, double energyKWhPerKm) {
     super(name, available, costPerHour);
     this.speedMetersPerSec = speedMetersPerSec;
     this.distanceMeters = distanceMeters;
     this.loadTimeMin = loadTimeMin;
     this.unloadTimeMin = unloadTimeMin;
     this.energyKWhPerKm = energyKWhPerKm;
 }

 public double cycleTimeMin() {
     double travelMin = (distanceMeters / speedMetersPerSec) / 60.0;
     return travelMin + loadTimeMin + unloadTimeMin;
 }

 public double energyPerCycleKWh() {
     return (distanceMeters / 1000.0) * energyKWhPerKm;
 }
}

//------------------ Operation ------------------
class Operation {
 String name;
 Map<String, Integer> resourceNeeds;
 double durationPerUnitMin;
 double durationBatchMin;
 boolean perUnit;
 double unitsPerResource;

 public Operation(String name, Map<String,Integer> resourceNeeds,
                  double durationPerUnitMin, double durationBatchMin,
                  boolean perUnit, double unitsPerResource) {
     this.name = name;
     this.resourceNeeds = resourceNeeds;
     this.durationPerUnitMin = durationPerUnitMin;
     this.durationBatchMin = durationBatchMin;
     this.perUnit = perUnit;
     this.unitsPerResource = unitsPerResource;
 }

 public double getTimeMin(int units, Map<String, Resource> pool) {
     double parallel = Double.MAX_VALUE;
     for(String rname: resourceNeeds.keySet()) {
         if(!pool.containsKey(rname)) throw new RuntimeException("Missing resource: " + rname);
         int qtyNeed = resourceNeeds.get(rname);
         int avail = pool.get(rname).available / qtyNeed;
         parallel = Math.min(parallel, avail * unitsPerResource);
     }
     if(parallel <= 0) return Double.POSITIVE_INFINITY;
     if(perUnit) return units / (parallel / durationPerUnitMin);
     else {
         int batches = (int)Math.ceil(units / parallel);
         return batches * durationBatchMin;
     }
 }

 public double getCost(int units, Map<String, Resource> pool) {
     double hours = getTimeMin(units, pool) / 60.0;
     double cost = 0.0;
     for(String rname: resourceNeeds.keySet()) {
         Resource r = pool.get(rname);
         if(r instanceof Human) cost += ((Human) r).getCost(hours);
         else if(r instanceof Machine) cost += ((Machine) r).getCost(hours);
         else if(r instanceof Material) cost += ((Material) r).getCostForUnits(units);
         else if(r instanceof AGV) {
             AGV agv = (AGV) r;
             int cycles = units; // assume 1 unit per trip
             cost += agv.getCost(hours); // operation cost
             cost += cycles * agv.energyPerCycleKWh() * 0.2; // energy cost
         }
     }
     return cost;
 }
}

//------------------ Process ------------------
class Process {
 String name;
 List<Operation> operations;

 public Process(String name) {
     this.name = name;
     this.operations = new ArrayList<>();
 }

 public void addOperation(Operation op) { operations.add(op); }
}

//------------------ Warehouse ------------------
class Warehouse {
 String name;
 Map<String, Resource> resources;
 List<Process> processes;

 public Warehouse(String name) {
     this.name = name;
     this.resources = new HashMap<>();
     this.processes = new ArrayList<>();
 }

 public void addResource(Resource r) { resources.put(r.name, r); }
 public void addProcess(Process p) { processes.add(p); }

 public void simulateBatch(int units) {
     double totalTime = 0.0;
     double totalCost = 0.0;
     for(Process p: processes) {
         double processTime = 0.0;
         double processCost = 0.0;
         for(Operation op: p.operations) {
             processTime += op.getTimeMin(units, resources);
             processCost += op.getCost(units, resources);
         }
         totalTime += processTime;
         totalCost += processCost;
         System.out.printf("Process %s: Time %.1f min, Cost %.2f\n", p.name, processTime, processCost);
     }
     System.out.printf("\nTotal Batch Time: %.1f min\n", totalTime);
     System.out.printf("Total Batch Cost: %.2f\n", totalCost);
 }
}

//------------------ Main ------------------
public class Main {
 public static void main(String[] args) {
     Warehouse wh = new Warehouse("MainWarehouse");

     // Resources
     wh.addResource(new Machine("CuttingMachine",2,15,5,0.2));
     wh.addResource(new Machine("WeldingMachine",2,20,8,0.2));
     wh.addResource(new Machine("PaintBooth",1,25,10,0.2));
     wh.addResource(new Human("Operator",2,20));
     wh.addResource(new Human("Assembler",3,18));
     wh.addResource(new AGV("AGV",4,5,1.5,200,0.5,0.5,0.8));
     wh.addResource(new Material("SteelPlate",1000,2.5));
     wh.addResource(new Material("PaintLiters",500,3.0));

     // Operations
     Operation cut = new Operation("Cutting", Map.of("CuttingMachine",1,"Operator",1,"SteelPlate",1),2,0,true,1);
     Operation weld = new Operation("Welding", Map.of("WeldingMachine",1,"Operator",1),3,0,true,1);
     Operation paint = new Operation("Painting", Map.of("PaintBooth",1,"Operator",1,"PaintLiters",1),0,30,false,4);
     Operation assemble = new Operation("Assembly", Map.of("Assembler",1,"AGV",1),1.5,0,true,1);

     // Processes
     Process pCut = new Process("Cutting");
     pCut.addOperation(cut);
     Process pWeld = new Process("Welding");
     pWeld.addOperation(weld);
     Process pPaint = new Process("Painting");
     pPaint.addOperation(paint);
     Process pAssemble = new Process("Assembly");
     pAssemble.addOperation(assemble);

     wh.addProcess(pCut);
     wh.addProcess(pWeld);
     wh.addProcess(pPaint);
     wh.addProcess(pAssemble);

     // Simulate batch
     wh.simulateBatch(200);
 }
}
