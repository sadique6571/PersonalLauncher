package com.sadique.launcher;

/** Quantized flex weights preserve each app's row while one card grows to 1.98 times its neighbours. */
final class GlassDockLayout {
    static final int SPANS=360; // Divisible by every supported column count.
    private GlassDockLayout(){}
    static int cardHeight(int cardWidthDp,int screenHeightDp){
        int available=Math.max(88,Math.min(136,(screenHeightDp-312)/2));
        return Math.min(available,Math.max(80,Math.round(Math.max(1,cardWidthDp)*.625f)));
    }
    static int reflectionHeight(int cardHeight){return Math.max(24,Math.min(48,Math.round(cardHeight*.36f)));}
    static int dockHeight(int cardWidthDp,int screenHeightDp){int card=cardHeight(cardWidthDp,screenHeightDp);return card+reflectionHeight(card)+68;}
    static int span(int position,int columns,int previous,int selected,float transition){
        columns=Math.max(2,Math.min(6,columns));transition=Math.max(0,Math.min(1,transition));
        int row=position/columns,col=position%columns;double total=0,before=0,through=0;
        for(int i=0;i<columns;i++){
            int index=row*columns+i;double weight=1;
            if(index==previous)weight+=.98*(1-transition);
            if(index==selected)weight+=.98*transition;
            if(i<col)before+=weight;if(i<=col)through+=weight;total+=weight;
        }
        return Math.max(1,(int)Math.round(SPANS*through/total)-(int)Math.round(SPANS*before/total));
    }
}
