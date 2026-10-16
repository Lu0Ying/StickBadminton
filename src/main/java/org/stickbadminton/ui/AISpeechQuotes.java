package org.stickbadminton.ui;

import org.stickbadminton.logic.GameProperties;

/**
 * AI 语录接口：五组台词（扣杀/领先嘲讽/落后反击/失分/扣杀被接住）按难度分文件实现，
 * 每档人机的说话风格与角色特质成对——1级菜鸟呆萌甩锅、2级普通业余正经、
 * 3级诱饵猫捉老鼠（反问+哼哼轻笑）、4级激进爆破腔（感叹号+痛快笑声）、
 * 5级精准冷面极简（句号收尾、提前宣判"结束了"）。
 * AISpeechBubble 播放时经 byDifficulty() 取当前档，不再内嵌台词数组。
 */
public interface AISpeechQuotes {
    /** AI 扣杀命中：挑衅腔 */
    String[] smash();
    /** AI 领先/拿到赛点：嘲讽腔 */
    String[] taunt();
    /** 被玩家领先/玩家拿到赛点：反击腔 */
    String[] pressure();
    /** AI 失分（普通丢分）：不甘腔 */
    String[] loss();
    /** AI 扣杀被玩家接住：惊讶腔 */
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
