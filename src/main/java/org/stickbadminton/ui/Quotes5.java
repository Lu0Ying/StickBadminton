package org.stickbadminton.ui;

/**
 * 5级"精准"语录——长辈式指导腔：不嘲讽、不炫耀，赢了指点、失分自省、
 * 被接住扣杀先肯定再点拨，落后也稳稳带节奏。
 * 与 Precise5 少扣必中、±5° 最准打法成对：像陪练的老手，句句是经验不是情绪。
 * 中文为主干，穿插少量英文术语（长辈教现代体育的口吻）。
 */
final class Quotes5 implements AISpeechQuotes {
    static final Quotes5 INSTANCE = new Quotes5();

    @Override public String[] smash() {
        return new String[]{
                "看好了，这一拍是这么发力的。",
                "脚步跟上，出手别犹豫。",
                "盯球，别盯人——记住这点。",
                "手腕再压一点，就是这样。",
                "年轻人，接球要提前站位。",
                "打空档，你看，很简单吧。",
                "放松，力量从腰上走。"
        };
    }

    @Override public String[] smashMiss() {
        return new String[]{
                "嗯，这一拍压过了。",
                "我的失误，网不背锅。",
                "出手早了半拍，见谅。",
                "这球是我的，重来。",
                "老了，手腕不听使唤了。",
                "总结：发力过头。",
                "丢这种球，说不过去。"
        };
    }

    @Override public String[] praise() {
        return new String[]{
                "好球！这一拍打得标准。",
                "可以，落点很刁。",
                "漂亮，年轻人学得快。",
                "这拍我得站起来接——不错。",
                "击球点选得好，记住这个感觉。",
                "嗯，有当年我几分风采。"
        };
    }

    @Override public String[] matchPoint() {
        return new String[]{
                "最后一分，别急，稳着来。",
                "赛点了，深呼吸。",
                "拿出你练的那些，就现在。",
                "越到收官越要沉住气。",
                "把节奏握在手里，慢慢来。",
                "打完这一分，胜负自明。"
        };
    }

    @Override public String[] taunt() {
        return new String[]{
                "打得不错，但还欠火候。",
                "看见差距了？这就是经验。",
                "别急，先学会走再学跑。",
                "你的问题在回位，回去多练。",
                "节奏被带走了，要沉住气。",
                "这点强度就乱了？多经历几场。",
                "慢慢来，我当年也是这么过来的。"
        };
    }

    @Override public String[] pressure() {
        return new String[]{
                "哦？这一分打得有模有样。",
                "好球，年轻人进步很快。",
                "有点意思，那我认真教教你。",
                "被追上了，说明你没藏私。",
                "行，把真本事拿出来。",
                "这才对嘛，逼出全力才有意思。",
                "不错，但比赛还没结束。"
        };
    }

    @Override public String[] loss() {
        return new String[]{
                "嗯，是我大意了。",
                "这球处理得糙了，见谅。",
                "老毛病，回位慢了半拍。",
                "输给年轻人体力，不冤。",
                "总结得好，下球改。",
                "是我轻敌，不怪你。",
                "这一分值——学到了。"
        };
    }

    @Override public String[] returned() {
        return new String[]{
                "接得漂亮！",
                "好小子，这球都接得住。",
                "可以啊，这一拍教得值。",
                "反应不错，有当年的我几分样。",
                "漂亮——这才叫打球。",
                "被你预判了？有意思。",
                "好，那下一拍再难点。"
        };
    }
}
