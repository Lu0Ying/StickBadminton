package org.stickbadminton.ui;

/**
 * 3级"诱饵"语录——狐狸式布局者：猫捉老鼠，说话带钩子（反问、玩味轻笑"哼哼"），
 * 失分是"计划被打乱"的懊恼，被接住扣杀是猎物挣脱预期的破防（纳尼系保留在此档）。
 * 与 Lure3 吊高球诱敌打法成对：嘴上从容，一切"都在计算内"。
 */
final class Quotes3 implements AISpeechQuotes {
    static final Quotes3 INSTANCE = new Quotes3();

    @Override public String[] smash() {
        return new String[]{
                "咬钩了。",
                "早等你这一拍了。",
                "哼哼，正中下怀。",
                "这位置，我算过了。",
                "どうぞ，请接。",
                "上钩的人，从不回头。",
                "哦？方向错了呢。",
                "鱼咬得越欢，网收得越紧。"
        };
    }

    @Override public String[] taunt() {
        return new String[]{
                "还以为是你在进攻？",
                "这盘棋，我看到结尾了。",
                "慢慢来，猎物急不得。",
                "再挣扎两下，我喜欢。",
                "将军，checkmate。",
                "你每一步，都踩在我画的线上。",
                "赢你？只是收网而已。"
        };
    }

    @Override public String[] pressure() {
        return new String[]{
                "哦？超出计算了。",
                "猎物咬破网了？有意思。",
                "行，那我换一手。",
                "别急，诱饵还多得是。",
                "这一分……是我小看你了。",
                "哼，兔子急了也会咬人。",
                "有点本事，值得我认真。"
        };
    }

    @Override public String[] loss() {
        return new String[]{
                "……失算。",
                "被你抓到规律了。",
                "这球不该丢的。",
                "うっ…大意了。",
                "行，换套方案。",
                "棋差一着。",
                "是运气，不是实力。",
                "下次不会再有这种球。"
        };
    }

    @Override public String[] returned() {
        return new String[]{
                "纳尼！？",
                "纳尼！你扯断了我的线！",
                "咬钩还反咬一口……",
                "这一下我没算到！",
                "纳尼！你怎么预判的！？",
                "有点意思，配当对手。",
                "行，那就认真点。",
                "居然挣脱了……baka。"
        };
    }
}
