package com.hexvane.aetherhaven.alchemy;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Hold the brew steady: one brew at the cauldron, played in beats and judged entirely here.
 *
 * <p>Heat runs from 0 to {@link #GAUGE}. Every beat the heat rises by itself; the player may stoke it higher or stir
 * it cooler. A glowing band drifts along the gauge, and a beat counts when the heat ends inside it. The share of
 * beats that counted decides the quality. Nothing here knows about time, the page or the engine: a beat happens when
 * the player presses a button, so lag can never cost a beat and nothing has to tick.
 */
public final class AlchemyBrew {
    public static final int GAUGE=100;
    public static final String FILE="Brewing.json";
    public enum Move { STIR, WAIT, STOKE }
    /** Carried by the flask as its ordinal. An unknown or missing value is GOOD. */
    public enum Quality {
        GOOD, GREAT, PERFECT;
        public static Quality of(int ordinal){return ordinal==2?PERFECT:ordinal==1?GREAT:GOOD;}
        /** "Great" for a sentence, "GREAT" for a banner. */
        public String label(){return name().charAt(0)+name().substring(1).toLowerCase(Locale.ROOT);}
    }

    /** The tuning, read from {@code Server/Aetherhaven/Alchemy/Brewing.json}. */
    public record Rules(int beats,int startHeat,int rise,int stoke,int stir,int bandWidth,int perExtraIngredient,int perMasteryLevel,
                        int minWidth,int forgiveness,int[] drift,int jitter,double turnChance,double greatShare,double perfectShare,
                        float greatDuration,float perfectDuration,float greatRestore,float perfectRestore) {
        static Rules parse(JsonObject data) {
            var moves=data.getAsJsonArray("Drift");var drift=new int[moves.size()];
            for(int i=0;i<drift.length;i++)drift[i]=moves.get(i).getAsInt();
            return new Rules(data.get("Beats").getAsInt(),data.get("StartHeat").getAsInt(),data.get("Rise").getAsInt(),data.get("Stoke").getAsInt(),
                data.get("Stir").getAsInt(),data.get("BandWidth").getAsInt(),data.get("BandWidthPerExtraIngredient").getAsInt(),
                data.get("BandWidthPerMasteryLevel").getAsInt(),data.get("BandMinWidth").getAsInt(),data.get("EdgeForgiveness").getAsInt(),drift,
                data.get("DriftJitter").getAsInt(),data.get("TurnChance").getAsDouble(),data.get("GreatShare").getAsDouble(),data.get("PerfectShare").getAsDouble(),
                data.get("GreatDuration").getAsFloat(),data.get("PerfectDuration").getAsFloat(),data.get("GreatRestore").getAsFloat(),data.get("PerfectRestore").getAsFloat());
        }
        /** A rule set a brew can be played with: every beat can be won, and a better brew is never worse. */
        boolean valid() {
            if(beats<1||beats>40||rise<0||stoke<1||stir<=rise||startHeat<0||startHeat>GAUGE||minWidth<2||bandWidth<minWidth||bandWidth>GAUGE/2
                ||forgiveness<0||jitter<0||drift.length!=3||turnChance<0||turnChance>1)return false;
            for(int move:drift)if(move-jitter<1||move+jitter>GAUGE/3)return false;
            return greatShare>0&&greatShare<=perfectShare&&perfectShare<=1&&greatDuration>=1&&perfectDuration>=greatDuration&&perfectDuration<=3
                &&greatRestore>=1&&perfectRestore>=greatRestore&&perfectRestore<=2;
        }
        /** More ingredients narrow the band, Orin's Mastery widens it. */
        public int width(int ingredients,int mastery) {
            return Math.clamp(bandWidth+(long)perExtraIngredient*(Math.clamp(ingredients,1,3)-1)+(long)perMasteryLevel*(Math.clamp(mastery,1,10)-1),minWidth,GAUGE/2);
        }
        /** More ingredients make the band move further each beat. */
        public int drift(int ingredients){return drift[Math.clamp(ingredients,1,3)-1];}
        public int greatBeats(){return (int)Math.ceil(greatShare*beats-1e-9);}
        public int perfectBeats(){return (int)Math.ceil(perfectShare*beats-1e-9);}
        public Quality quality(int beatsInside){return beatsInside>=perfectBeats()?Quality.PERFECT:beatsInside>=greatBeats()?Quality.GREAT:Quality.GOOD;}
        /** How much longer a lasting effect runs: 1 for Good. */
        public float duration(Quality quality){return quality==Quality.PERFECT?perfectDuration:quality==Quality.GREAT?greatDuration:1f;}
        /** How much more an instant restore gives: 1 for Good. */
        public float restore(Quality quality){return quality==Quality.PERFECT?perfectRestore:quality==Quality.GREAT?greatRestore:1f;}
        /** Whole percent above a Good potion, for text: 25 for a factor of 1.25. */
        public static int percent(float factor){return Math.round((factor-1f)*100f);}
    }
    private static volatile Rules loaded;
    /** The rules in force. The bundled file is the fallback, so a broken pack file can never stop a brew. */
    public static Rules rules() {
        var rules=loaded;
        if(rules==null){rules=load(AlchemyBrew.class.getClassLoader());loaded=rules;}
        return rules;
    }
    static Rules load(ClassLoader loader) {
        var data=AlchemyData.read(loader,FILE,candidate->{try{return Rules.parse(candidate).valid();}catch(RuntimeException ex){return false;}});
        if(data==null)throw new IllegalStateException("Aetherhaven's bundled "+AlchemyData.FOLDER+"/"+FILE+" is missing or invalid");
        return Rules.parse(data);
    }

    private final Rules rules;
    private final SplittableRandom random;
    private final int width,drift;
    private int heat,centre,direction=1,next,beat,inside;
    private boolean lastCounted;

    /**
     * @param seed decides how the band drifts; the same seed and the same moves always give the same brew
     * @param ingredients plants in the mixture, 1 to 3
     * @param mastery Orin's Alchemist Mastery, 1 to 10
     */
    public AlchemyBrew(Rules rules,long seed,int ingredients,int mastery) {
        this.rules=rules;random=new SplittableRandom(seed);
        width=rules.width(ingredients,mastery);drift=rules.drift(ingredients);
        heat=Math.clamp(rules.startHeat(),0,GAUGE);centre=Math.clamp(heat,low(),high());
        if(random.nextBoolean())direction=-1;
        plan();
    }
    private int low(){return (width+1)/2;}
    private int high(){return GAUGE-(width+1)/2;}
    /** Decides the band's next position now, so the direction shown to the player is always the truth. */
    private void plan() {
        int step=drift+random.nextInt(-rules.jitter(),rules.jitter()+1);
        int target=centre+direction*step;
        if(target>high()||target<low()){direction=-direction;target=centre+direction*step;}
        next=Math.clamp(target,low(),high());
    }
    public int heat(){return heat;}
    public int centre(){return centre;}
    public int width(){return width;}
    /** +1 when the band will move up this beat, -1 when it will move down. */
    public int direction(){return direction;}
    /** Where the band usually ends up this beat. The real move is a little more or less. */
    public int expectedCentre(){return Math.clamp(centre+direction*drift,low(),high());}
    public int beat(){return beat;}
    public int beats(){return rules.beats();}
    public int inside(){return inside;}
    public boolean lastCounted(){return lastCounted;}
    public boolean finished(){return beat>=rules.beats();}
    /** Where the heat would be after this move. */
    public int after(Move move) {
        return Math.clamp(heat+rules.rise()+(move==Move.STOKE?rules.stoke():move==Move.STIR?-rules.stir():0),0,GAUGE);
    }
    /** True when this heat counts for a band centred here: inside it, or within the forgiven margin of its edge. */
    public boolean counts(int heatValue,int bandCentre){return Math.abs(2*heatValue-2*bandCentre)<=width+2*rules.forgiveness();}
    /**
     * Plays one beat: the heat moves, the band drifts, and the beat is counted if the heat ended inside the band.
     *
     * @return whether the beat counted; false too when the brew is already finished
     */
    public boolean play(Move move) {
        if(finished()||move==null)return false;
        heat=after(move);centre=next;
        lastCounted=counts(heat,centre);
        if(lastCounted)inside++;
        beat++;
        if(random.nextDouble()<rules.turnChance())direction=-direction;
        plan();
        return lastCounted;
    }
    /** GOOD until every beat has been played: a brew bottled early is an ordinary potion. */
    public Quality quality(){return finished()?rules.quality(inside):Quality.GOOD;}
}
