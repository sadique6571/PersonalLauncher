package com.sadique.launcher;

/** Quantized flex weights preserve each app's row while one card grows to 1.98 times its neighbours. */
final class GlassDockLayout {
    static final int SPANS=360; // Divisible by every supported column count.
    private GlassDockLayout(){}
    static int cardHeight(int cardWidthDp,int screenHeightDp){
        int available=Math.max(88,Math.min(136,(screenHeightDp-312)/2));
        return Math.min(available,Math.max(80,Math.round(Math.max(1,cardWidthDp)*.625f)));
    }
    static int iconSide(int contentWidthDp,int screenHeightDp){
        int heightLimit=Math.max(64,Math.min(128,(screenHeightDp-300)/2));
        return Math.max(48,Math.min(heightLimit,Math.round(Math.max(1,contentWidthDp)*.78f)));
    }
    static int wideBodyHeight(int contentWidthDp,int screenHeightDp){return Math.min(iconSide(contentWidthDp,screenHeightDp),Math.max(48,Math.round(contentWidthDp/1.45f)));}
    static int wideBodyWidth(int availableWidth,int height){return Math.max(1,Math.min(availableWidth,Math.round(height*1.45f)));}
    static int captionHeight(int textSp,float fontScale){return Math.max(28,Math.min(64,Math.round(Math.max(12,Math.min(22,textSp))*Math.max(1,fontScale)*1.5f+8)));}
    static int iconTileHeight(int contentWidthDp,int screenHeightDp,int captionHeight){return wideBodyHeight(contentWidthDp,screenHeightDp)+captionHeight;}
    static int reflectionHeight(int cardHeight){return Math.max(16,Math.min(32,Math.round(cardHeight*.20f)));}
    static int dockHeight(int cardWidthDp,int screenHeightDp){int tile=iconTileHeight(cardWidthDp,screenHeightDp,captionHeight(16,1));return tile+reflectionHeight(tile)+60;}
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
