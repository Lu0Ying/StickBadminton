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
