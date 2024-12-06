package de.atruvia.partitioner;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class RangePartinionierer implements Partitioner {
    @Override
    public Map<String, ExecutionContext> partition(final int gridSize) {
        int min = 1;
        int max = 1000;
        int targetSize=(max - min)/gridSize + 1;

        Map<String, ExecutionContext> partitions = new HashMap<String, ExecutionContext>();
        int number = 0;
        int start = min;
        int end = start + targetSize + 1;

        while(start <= max) {
            ExecutionContext value = new ExecutionContext();
            partitions.put("partition" + number ++, value);
            if(end>= max) {
                end = max;
            }
            value.putInt("minValue", start);
            value.putInt("maxValue", end);
            start+= targetSize;
            end+=targetSize;
        }
        return partitions;
    }
}
