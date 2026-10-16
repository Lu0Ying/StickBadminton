package org.stickbadminton.ui;

import org.stickbadminton.logic.GameProperties;

/**
 * AI 语录接口：八组台词按难度分文件实现，每档人机的说话风格与角色特质成对——
 * 1级菜鸟呆萌甩锅、2级普通业余正经、3级诱饵猫捉老鼠（反问+哼哼轻笑）、
 * 4级激进爆破腔（感叹号+痛快笑声）、5级长辈指导腔（指点与自省）。
 * AISpeechBubble 播放时经 byDifficulty() 取当前档，不再内嵌台词数组。
 */
public interface AISpeechQuotes {
    /** AI 扣杀得分（结果落地后才说）：成功句 */
    String[] smash();
    /** AI 扣杀直接失误（砸网/出界没被回击）：自失腔 */
    String[] smashMiss();
    /** 对手扣杀命中：夸赞腔 */
    String[] praise();
    /** 任意一方拿到赛点：打气腔（给自己鼓劲） */
    String[] matchPoint();
    /** AI 领先（非赛点、非扣杀得分）：嘲讽腔 */
    String[] taunt();
    /** 被玩家领先/玩家赛点：反击腔 */
    String[] pressure();
    /** AI 失分（普通丢分/对拉丢分）：不甘腔 */
    String[] loss();
    /** AI 扣杀被玩家回击瞬间：惊讶腔（出拍沉默，回击才出句） */
    String[] returned();

    /** 按当前难度取语录（气泡只在单人创建，难度=选人所选；越界按3级兜底） */
    static AISpeechQuotes byDifficulty() {
        switch (GameProperties.difficultyLevel) {
            case 1: return Quotes1.INSTANCE;
            case 2: return Quotes2.INSTANCE;
            case 4: return Quotes4.INSTANCE;
            case 5: return Quotes5.INSTANCE;
            default: return Quotes3.INSTANCE;
        }
    }
}
