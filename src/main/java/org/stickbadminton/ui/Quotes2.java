package org.stickbadminton.ui;

/**
 * 2级"普通"语录——正常业余爱好者：有干劲也认失误，赢了不飘、输了反省。
 * 与 Normal2 行为特质（能接能打、偶尔下网）成对：正经但不老练。
 */
final class Quotes2 implements AISpeechQuotes {
    static final Quotes2 INSTANCE = new Quotes2();

    @Override public String[] smash() {
        return new String[]{
                "看拍！",
                "这拍怎么样？",
                "接招！",
                "到位了这球！",
                "发力！",
                "压住你了！",
                "再来一拍重的！",
                "盯紧了，Ready？"
        };
    }

    @Override public String[] taunt() {
        return new String[]{
                "状态不错，Nice~",
                "承让。",
                "节奏被我带着走吧。",
                "还差几分，稳住。",
                "你防守有破绽。",
                "这几拍打得很舒服。"
        };
    }

    @Override public String[] pressure() {
        return new String[]{
                "还没完呢！",
                "追回来！",
                "咬住比分。",
                "深呼吸……再来！",
                "我还没输。",
                "一分一分追。"
        };
    }

    @Override public String[] loss() {
        return new String[]{
                "哎，差一点……",
                "我的失误。",
                "这球处理得不好……",
                "回位慢了。",
                "Oops…可惜了。",
                "下一拍补回来。",
                "大意了。"
        };
    }

    @Override public String[] returned() {
        return new String[]{
                "哦？可以啊！",
                "被你预判了？",
                "这都被你接住……",
                "反应挺快！",
                "行，有两下子。Nice shot！"
        };
    }
}
