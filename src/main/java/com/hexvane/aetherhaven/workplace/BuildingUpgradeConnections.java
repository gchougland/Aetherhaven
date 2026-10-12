package com.hexvane.aetherhaven.workplace;

import java.util.*;
import java.util.function.ToIntFunction;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Node;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Kind;

/** Orthogonal tree paths. Shared trunks are painted once with the strongest reached state. */
public final class BuildingUpgradeConnections {
    private BuildingUpgradeConnections() {}
    public record Stroke(int x,int y,int width,int height,int state) {}
    private record Rail(boolean horizontal,int fixed) {}
    private record Span(int start,int end,int state) {}
    public static List<Stroke> build(List<Node> nodes,ToIntFunction<Node> state) {
        Map<Rail,List<Span>> rails=new LinkedHashMap<>();
        for(var to:nodes)for(String parent:to.parents()) {
            var from=nodes.stream().filter(n->n.id().equals(parent)).findFirst().orElseThrow();
            int ax=from.x()+87,ay=from.y()+112,bx=to.x()+87,by=to.y(),s=state.applyAsInt(to);
            if(to.kind()==Kind.PRODUCTION && to.branch().equals("ADAMANTITE") && from.x()!=to.x()) {
                // Side branches feed storage through inner ports. Their own next tiers stay on
                // separate outer trunks, so a merge cannot imply an unrelated prerequisite.
                int port=from.x()<to.x()?from.x()+174:from.x();
                int gutter=from.x()<to.x()?220:420;
                add(rails,port,from.y()+56,gutter,from.y()+56,s);
                add(rails,gutter,from.y()+56,gutter,by-20,s);
                add(rails,gutter,by-20,bx,by-20,s);add(rails,bx,by-20,bx,by,s);
            } else if(from.id().equals("home")&&(to.kind()==Kind.SLOT||to.kind()==Kind.SMELTING
                    ||to.kind()==Kind.RESTAURANT&&to.branch().equals("PROVISIONS"))) {
                // Restaurant provisions start below the Menu column, so their trunk uses the left gutter too.
                int gutter=to.kind()==Kind.SMELTING?420:220;
                add(rails,ax,ay,ax,ay+24,s);add(rails,ax,ay+24,gutter,ay+24,s);
                add(rails,gutter,ay+24,gutter,by-20,s);add(rails,gutter,by-20,bx,by-20,s);
                add(rails,bx,by-20,bx,by,s);
            } else if(from.y()==to.y()) {
                int left=Math.min(from.x(),to.x()),right=Math.max(from.x(),to.x());
                add(rails,left+174,by+56,right,by+56,s);
            } else {
                int mid=from.id().equals("home")?ay+24:by-20;
                add(rails,ax,ay,ax,mid,s);add(rails,ax,mid,bx,mid,s);add(rails,bx,mid,bx,by,s);
            }
        }
        var result=new ArrayList<Stroke>();
        for(var entry:rails.entrySet()) {
            var points=new TreeSet<Integer>();
            entry.getValue().forEach(span->{points.add(span.start);points.add(span.end);});
            Integer previous=null;
            for(int end:points) {
                if(previous!=null) {
                    int start=previous;
                    int strongest=entry.getValue().stream().filter(span->span.start<=start&&span.end>=end).mapToInt(Span::state).max().orElse(-1);
                    if(strongest>=0) {
                        var rail=entry.getKey();
                        result.add(rail.horizontal?new Stroke(start,rail.fixed,end-start,2,strongest):new Stroke(rail.fixed,start,2,end-start,strongest));
                    }
                }
                previous=end;
            }
        }
        return List.copyOf(result);
    }
    private static void add(Map<Rail,List<Span>> rails,int x1,int y1,int x2,int y2,int state) {
        if(x1==x2&&y1==y2)return;
        boolean horizontal=y1==y2;
        var rail=new Rail(horizontal,horizontal?y1:x1);
        rails.computeIfAbsent(rail,k->new ArrayList<>()).add(new Span(horizontal?Math.min(x1,x2):Math.min(y1,y2),horizontal?Math.max(x1,x2):Math.max(y1,y2),state));
    }
}
