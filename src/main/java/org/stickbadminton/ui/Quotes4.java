package org.stickbadminton.ui;

/**
 * 4级"激进"语录——狂战士：短促爆破、感叹号连发、痛快笑声（"哈哈哈""痛快！"），
 * 越落后越兴奋、失分就咆哮反扑，被接住扣杀先震惊再加倍进攻。
 * 与 Aggro4 多跳多扣、35% 砸网的"凶但有破绽"成对：追求的是战斗本身的爽感。
 */
final class Quotes4 implements AISpeechQuotes {
    static final Quotes4 INSTANCE = new Quotes4();

    @Override public String[] smash() {
        return new String[]{
                "哈！接好了！",
                "砸穿你！",
                "再来一记重的！",
                "躲啊！你躲得开吗！",
                "这一拍就是全部！",
                "别挡路！",
                "碾过去！",
                "覚悟しろ——！"
        };
    }

    @Override public String[] taunt() {
        return new String[]{
                "看到了吗！这就是力量！",
                "站起来！我还没打够！",
                "你就这点反抗吗！",
                "压倒你了！继续啊！",
                "弱！太弱了，weak！",
                "气势在我这边！",
                "敢接吗？不敢就对了！"
        };
    }

    @Override public String[] pressure() {
        return new String[]{
                "哈！终于有点意思了！",
                "好！这才配当对手！",
                "痛快！再来！",
                "我的血在烧！",
                "别停！拿出全力来！",
                "落后？我才刚热身！",
                "越疼，我打得越狠！"
        };
    }

    @Override public String[] loss() {
        return new String[]{
                "啧！",
                "下一球我撕了你！",
                "くそっ！刚才太急了！",
                "别得意！马上讨回来！",
                "我生气了。",
                "全力！下一球全力！",
                "疼……哈哈哈，痛快！"
        };
    }

    @Override public String[] returned() {
        return new String[]{
                "居然接住了！？",
                "哈——好！那就再快点！",
                "挡得下来？再来！",
                "这一下我不会收力！",
                "行！那就拼了！",
                "痛快！接我下一拍！",
                "ハッ！正合我意！"
        };
    }
}
