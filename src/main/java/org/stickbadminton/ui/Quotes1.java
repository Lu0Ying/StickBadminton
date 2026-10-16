package org.stickbadminton.ui;

/**
 * 1级"菜鸟"语录——呆萌状况外：得分像中奖、失分怪装备、被接住扣杀只会懵。
 * 与 Noob1 行为特质（迟钝、常下网）成对：赢了也不知怎么赢的，输了甩锅。
 */
final class Quotes1 implements AISpeechQuotes {
    static final Quotes1 INSTANCE = new Quotes1();

    @Override public String[] smash() {
        return new String[]{
                "我打到了？",
                "嘿嘿，碰巧的！",
                "别、别小看我！",
                "这球…够快吧？",
                "被我蒙中了！",
                "うわー！过去了！",
                "我也不太懂，反正过去了。"
        };
    }

    @Override public String[] smashMiss() {
        return new String[]{
                "啊……飞出去了……",
                "呜，砸网了！",
                "我就说拍子不对！",
                "这、这不算！",
                "差一点点……真的就一点点！",
                "怎么没过去呀！",
                "手滑！又是手滑！",
                "我我我我不是故意的！"
        };
    }

    @Override public String[] praise() {
        return new String[]{
                "哇……你怎么打到的！",
                "好、好厉害……",
                "这一拍我接不住啦",
                "你练过吧？！",
                "教教我嘛……",
                "诶诶，好球！我服了！",
                "被你帅到了……"
        };
    }

    @Override public String[] matchPoint() {
        return new String[]{
                "还差一分……加油加油！",
                "我、我还能打！",
                "别让我输在这里呀……",
                "手心出汗了……再来！",
                "稳住稳住……",
                "求求了，让我赢一次吧！"
        };
    }

    @Override public String[] taunt() {
        return new String[]{
                "嘿嘿，我好像要赢了？",
                "运气也是实力！",
                "你让我的吧？",
                "再赢一点点就…嘿嘿。",
                "我今天手感不错诶！",
                "耶！赢啦！Yeah!"
        };
    }

    @Override public String[] pressure() {
        return new String[]{
                "等、等等！",
                "别过来别过来！",
                "我还没准备好……",
                "这、这也太快了吧！",
                "呜……",
                "再给我一次机会！"
        };
    }

    @Override public String[] loss() {
        return new String[]{
                "哎呀，手滑了……",
                "球怎么不听话！",
                "地太滑了……",
                "差一点点就……",
                "我拍子的问题！",
                "呜…うわー，输了！",
                "鞋子踩到东西了……",
                "下一球下一球！"
        };
    }

    @Override public String[] returned() {
        return new String[]{
                "诶？接住了？",
                "诶诶诶——！",
                "骗人，你怎么接到的！",
                "えっ！？骗人的吧！",
                "球怎么回来了！？",
                "你、你看错了吧？"
        };
    }
}
