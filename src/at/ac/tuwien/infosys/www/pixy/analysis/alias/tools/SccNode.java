package at.ac.tuwien.infosys.www.pixy.analysis.alias.tools;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import at.ac.tuwien.infosys.www.pixy.conversion.Variable;

// node in the SccGraph
public class SccNode {

    private Variable label;
    // Map SccNode -> SccEdge (i.e., target node -> edge)
    private Map<SccNode, SccEdge> doubleEdges;

    public SccNode(Variable label) {
        this.label = label;
        this.doubleEdges = new HashMap<SccNode, SccEdge>();
    }

    public Variable getLabel() {
        return this.label;
    }

    public Set<SccNode> getDoubleTargets() {
        return new HashSet<SccNode>(this.doubleEdges.keySet());
    }

    public void addDoubleEdge(SccEdge edge, SccNode target) {
        this.doubleEdges.put(target, edge);
    }
}