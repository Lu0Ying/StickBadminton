package org.stickbadminton.entity;
import org.stickbadminton.logic.GameProperties;
import org.stickbadminton.logic.MatchAimAssist;
import org.stickbadminton.logic.MissTrace;
import java.util.Random;

public class ComputerDecision {
    // 动作控制标志
    public boolean isLighthit = false;
    public boolean isHeavyhit = false;
    public boolean isMoveLeft = false;
    public boolean isMoveRight = false;
    public boolean isJump = false;
    public boolean isShot = false;
    public boolean isJumpCooldown = false;

    // 位置和状态信息（由调用方每帧传入）
    public double computerX = 0.0;
    public double computerY = 0.0;
    public double badmintonX = 0.0;
    public double badmintonY = 0.0;
    public double badmintonSpeedX = 0.0;
    public double badmintonSpeedY = 0.0;
    public double opponentX = 0.0;
    public double opponentY = 0.0;
    public double racketRadius;
    public double hitAreaRadius;
    public double highShotDistance = 150.0;
    public double defenceOpponentDistance = 100.0;
    public double superShotPoint;
    public double superShotAngleDeg = -1.0;
    // 扣杀意图：superShot 路径置位（过网仿真通过后的爆发一拍）。
    // 平抽(middleShot)/防守重击不算扣杀——接触层仅对扣杀做"朝上角镜像成下压角"
    // 的爆发处理，避免普通重击也变成 2500 档陡下压
    public boolean isSmashIntent = false;
    public double difficultyLevel;

    // 反应/起跳门槛值已移入各难度档案（Noob1/Normal2/Lure3/Aggro4/Precise5），
    // 这里只保留"预设失误回合"的横移速度系数（跑位欠步手感）
    private static final double MISS_MOVE_SCALE = 0.55;

    // 网前判定参数
    private static final double NET_X_WINDOW = 140.0;
    // 前半场窗口：本方半场(网到墙约380px)的一半。前场内的高球优先起跳扣杀，
    // 后场不再为站立本就够得到的高球起跳挑球
    private static final double FRONT_COURT_WINDOW = 190.0;
    // 中前场/后场分界：离网 ≤250px 为中前场(扣杀高概率区)，>250px 为后场(允许扣但低概率)，
    // variedShot 也按该分界在后场偏向重击
    private static final double SMASH_COURT_WINDOW = 250.0;
    // 提前起跳扣杀：球至少高出肩点 90px 才值得提前跳（更低的球等进圈再决策还来得及）
    private static final double EARLY_SMASH_MIN_H = 90.0;
    // 网前拦截阈值：球快落到前场(离网190px内)时高度门槛放宽，AI 直接网前跳起拦截扣杀
    private static final double EARLY_SMASH_MIN_H_NET = 30.0;
    // 提前起跳的水平对齐容差：粗筛门槛，真实裁判是 earlySmashConnectable 的逐帧推演
    // (含风存期横移收差)。放宽到150px：慢速球允许边跑边跳，不必先跑到后场等球再接
    private static final double EARLY_SMASH_ALIGN = 150.0;
    // 注：曾尝试在贴墙60px内加额外"场边严判"（扫掠距×0.75/连续2帧），两版均回归
    // （误拒真实可接球 → AI 不跳站定接不到），已回退至 v1.0 的真实相位推演口径——
    // "场边跳起接不到"的根因是 SoundPlay 音效异常中断 heavyHit 出球，已单独修复
    // 扣杀概率分层（圈内/提前、后场/中前场）已移入各难度档案（inRing*/early* 字段），
    // 3级常规档(35/60、40/75)即 1/2 级与 3 级共用的默认值，档案各自声明
    // 起跳 0.1s(挥拍接触时刻)肩膀抬高的像素：v0=700、g=35/帧，6帧累计约60px，
    // 高慢球按抬高后的肩点重算切线出球角，判断能否压成扣杀
    private static final double JUMP_RAISE_PX = 60.0;
    // 对手位置观察：每 30 帧(0.5s)一个瞬时时间片，不用每帧跟踪；
    // 时间片内拍型判定保持稳定，击球更有套路感
    private static final int OBSERVE_INTERVAL = 30;
    // 观察档位线（对手离网距离）：≤180 视为压在前场，>300 视为退守后场
    private static final double OBS_FRONT_NET = 180.0;
    private static final double OBS_BACK_NET = 300.0;
    // 时间片间净位移超过 20px 才算明显移动（防抖，避免微晃误判趋势）
    private static final double OBS_TREND_MIN = 20.0;
    private static final double NET_Y_MARGIN_ABOVE = 130.0;
    private static final double NET_Y_MARGIN_BELOW = 20.0;
    private static final double NET_ALIGN_DEADZONE = 4.0;
    private static final double NET_SAFE_OFFSET = 10.0;  // 稍加大安全距离，减少贴网失手
    private static final double WALL_BOUNCE_GAP = 22.0;  // 增大贴墙阈值

    // 杀球防守参数（新）
    // 杀球判定口径（包内共享：StickMan 接杀后触发回合精度惩罚用同一阈值）
    static final double SMASH_SPEED_THRESHOLD = 900.0;   // 合速度阈值
    static final double SMASH_VY_THRESHOLD    = 350.0;   // 竖直向下速度阈值（y正向下）
    private static final double SMASH_INTERCEPT_Y_MIN = 30.0;    // 肩上方最小拦截高度
    private static final double SMASH_INTERCEPT_Y_MAX = 90.0;    // 肩上方最大拦截高度
    private static final double SMASH_CONTACT_OFFSET  = 6.0;     // 接触时的本方侧偏移，避免贴身打空
    private static final double SMASH_HIT_MARGIN      = 12.0;    // 击球圈放宽值

    // 扣杀决策过网仿真的安全边距上限(px)：接触发生在决策后约0.1s，球仍在下落，
    // 实际边距按 badmintonSpeedY*0.1 动态计算并以此封顶（固定45会把正常扣杀全拒掉）
    private static final double SMASH_LAG_MARGIN_CAP = 45.0;
    // 决策层压平迭代上限(度)：切线角天然比过网角更下压，向本方前上方每次调5°直到能过网；
    // 超过该上限说明本质不是扣杀球，交回常规分支（真正出球时 StickMan 接触层还有安全网兜底）
    private static final double SMASH_FLATTEN_MAX_DEG = 30.0;

    // 跨帧记忆：用于判定“刚刚墙反弹”
    private static double prevBallX = Double.NaN;
    private static double prevBallVX = Double.NaN;
    private static int wallBounceCooldownFrames = 0;   // 墙反弹后禁止起跳的帧数
    private static final int WALL_BOUNCE_COOLDOWN_DEFAULT = 12; // ~0.2s @60fps
    // 接杀反应闩锁（跨帧，ComputerDecision 每帧重建与墙反弹冷却同模式，按半场侧索引）：
    // 来球判为扣杀的整拍只掷一次 smashReceiveChance——拒绝后 latch 保持"拒"，
    // 本拍期间 AI 不再反应(站定看着球飞过/落地)；击到或球落地时复位
    private static final int[] smashLatch = new int[]{0, 0}; // 0=未掷 1=过 2=拒
    // 对手位置时间片采样：static 跨帧持久（ComputerDecision 每帧重建，与墙反弹冷却同模式），
    // 按半场侧索引（0=side1 左侧AI，1=side-1 右侧AI）避免左右两个 AI 互相污染
    private static final double[] oppSampleLast = new double[]{Double.NaN, Double.NaN};
    private static final double[] oppSamplePrev = new double[]{Double.NaN, Double.NaN};
    private static final int[] oppSampleCd = new int[]{0, 0};
    // 跳杀后的横移跟随窗口(帧)：起跳后约12帧(风存6+挥拍)内 AI 必须朝球当前位置收差，
    // 不能走 adjustPosition 的落地点——有水平速度的球落点常在球的后方，
    // 朝落点跑会让起跳位置太靠后(网前/场边)而挥空。按半场侧索引
    private static final int[] jumpSmashFollow = new int[]{0, 0};

    // 预判与状态
    private double predictedLandingX;
    private double predictedLandingY;
    // 本帧观察结果（时间片内恒定，构造流程里 observeOpponent 赋值、variedShot 读取）
    private double observedOppNetDist = Double.NaN;
    private boolean observedOppRushNet = false;   // 对手正朝网压上
    private boolean observedOppFallBack = false;  // 对手正向后退守
    private boolean isBallApproaching = false;
    private Random random = new Random();
    // G 键调试：p1 满级 AI。决策全部在构造器内完成，必须构造前定级，
    // 事后改 difficultyLevel 来不及（shouldReact 等随机门槛已在构造中执行过）
    private boolean maxLevel = false;
    // 体力管理（难度 ≥3 或 G 键满级启用）：低体力收敛跳跃/重击、站桩回血、按实际移速预判；
    // 1-2级不启用，保持原有的“乱”行为
    private boolean smartEnergy = false;
    // 当前档难度档案：反应/起跳/扣杀概率/兜底/精度惩罚等特质参数的唯一来源
    // （满级时取 5 级档案——联机同轨的随机点调用频率不变）
    private AIDifficultyProfile profile = Precise5.INSTANCE;
    private double energy = 100.0;
    // 对手(玩家)体力：3/5 级非满级扣杀门读取
    private double opponentEnergy = 100.0;
    // 残血扣杀门：玩家体力低于该值才允许扣杀（3级20、5级30；1/2/4级不设门，满级不走此门）
    private static final double SMASH_OPPONENT_LOW_ENERGY_L3 = 20.0;
    private static final double SMASH_OPPONENT_LOW_ENERGY_L5 = 30.0;
    // StickMan 传入的实际移速（体力插值），仅体力管理路径参与预判
    private double effectiveMoveSpeed;
    // 失分诊断：本帧跳跃/扣杀被拒原因码（仅 MissTrace 开启时被各拒绝点赋值）
    private String traceRej = "";
    // 本构造的半场与球是否在本方半场（StickMan 挥拍起手时做"本拍真能扫到"预演用）
    private int traceSide = 0;
    private boolean onMyHalfNow = false;

    public ComputerDecision() {}

    public ComputerDecision(boolean maxLevel, int side,
                            double hitCenterX, double hitCenterY, boolean isShotCooldown, boolean isJumpCooldown,
                            double badmintonX, double badmintonY, double badmintonSpeedX, double badmintonSpeedY,
                            double opponentX, double opponentY,
                            double energyRemain, double effectiveMoveSpeed, double opponentEnergy)
    {
        this.maxLevel = maxLevel;
        this.energy = energyRemain;
        this.effectiveMoveSpeed = effectiveMoveSpeed;
        this.opponentEnergy = opponentEnergy;
        // 难度档案先定级（反应/起跳/扣杀概率/兜底都读它），满级沿用5级档案
        this.profile = maxLevel ? Precise5.INSTANCE : AIDifficultyProfile.byDifficulty();
        this.smartEnergy = maxLevel || profile.energyManaged();
        // 初始化基本信息
        this.traceSide = side;
        this.onMyHalfNow = (badmintonX - GameProperties.netPosition) * side <= 0;
        this.badmintonSpeedX = badmintonSpeedX;
        this.badmintonSpeedY = badmintonSpeedY;
        this.badmintonX = badmintonX;
        this.badmintonY = badmintonY;
        this.computerX = hitCenterX;
        this.computerY = hitCenterY;
        this.opponentX = opponentX;
        this.opponentY = opponentY;
        this.isJumpCooldown = isJumpCooldown;
        this.superShotPoint = computerY - 30;

        // 决策系数直接取档案（1级0.5、2级0.8、3级0.9、4/5级1.0）；满级固定1.0，
        // 不改全局档位号，对手 AI 不被顺带抬档
        this.difficultyLevel = maxLevel ? 1.0 : profile.coeff;
        if (maxLevel) {
            this.racketRadius = GameProperties.racketRadius;
            this.hitAreaRadius = GameProperties.hitAreaRadius;
        }
        // 判圈随难度收缩，保留低概率随机满圈的历史行为（满系数档两分支同值，恒满圈）
        if (random.nextDouble() > difficultyLevel * 0.1) {
            this.racketRadius = GameProperties.racketRadius * difficultyLevel;
            this.hitAreaRadius = GameProperties.hitAreaRadius * difficultyLevel;
        } else {
            this.racketRadius = GameProperties.racketRadius;
            this.hitAreaRadius = GameProperties.hitAreaRadius;
        }


        // 更新墙反弹冷却
        updateWallBounceCooldown();

        // 预判球的落点
        predictBallLanding();

        // 判断球是否向我方飞来
        isBallApproaching = isBallComingToMySide(side);

        // 执行决策
        if (isBallApproaching) {
            if (shouldReact()) {
                executeStrategy(side, isShotCooldown);
            } else {
                traceRej = "noReact"; // 反应随机门拒绝（低难度 shouldReact）
            }
        } else if (badmintonY < GameProperties.floorBallY - 50) {
            // 无球/回位
            offBallPositioning(side);
        }

        emitDecisionTrace(side, isShotCooldown);

        // 记忆当前球状态
        prevBallX = badmintonX;
        prevBallVX = badmintonSpeedX;
    }

    // 失分诊断：给本帧追加一个拒绝原因码（仅开启时生效，多码逗号分隔）
    private void rejMark(String code) {
        if (!MissTrace.isEnabled()) return;
        traceRej = traceRej.isEmpty() ? code : traceRej + "," + code;
    }

    // 失分诊断：每帧一行（仅 MissTrace 开启时拼串；含本帧所有跳跃/扣杀拒绝原因码）
    // 格式：D f=? s=? | ball x,y,vx,vy | ai x,cd,cdjmp | acc=0/1 | shot/kind/jump | rej=code
    // rej 码的裁读：smProb=圈内概率扣杀被概率门拒(need,roll=故意不扣)、
    // lSmash=1-2级低难度扣杀概率门拒(need=故意不扣)、
    // eOppE/prOppE=3/5级残血扣杀体力门拒(3级玩家≥20、5级≥30，非残血不扣杀)、
    // eXxx=提前跳杀各道硬门、jXxx=普通跳跃门、dcd=击球圈未达、cd=冷却
    private void emitDecisionTrace(int side, boolean shotCooldown) {
        if (!MissTrace.isEnabled()) return;
        boolean onMySide = (side == 1) ? (badmintonX < GameProperties.netPosition)
                : (badmintonX > GameProperties.netPosition);
        double gap = Math.abs(computerX - badmintonX);
        double dist = Math.sqrt(gap * gap + (computerY - badmintonY) * (computerY - badmintonY));
        String kind = isSmashIntent ? "smash" : isHeavyhit ? "heavy" : isLighthit ? "light" : "-";
        MissTrace.decision(String.format(
                "D f=%d s=%s | ball %.0f,%.0f,%.0f,%.0f | ai %.0f cd=%b cdjmp=%b | acc=%d dist=%.0f | shot=%b %s jump=%b | rej=%s",
                MissTrace.frame(), side > 0 ? "L" : "R",
                badmintonX, badmintonY, badmintonSpeedX, badmintonSpeedY,
                computerX, shotCooldown, isJumpCooldown,
                onMySide ? 1 : 0, dist,
                isShot, kind, isJump, traceRej));
    }

    // 决策层预判用的移速：体力管理按实际移速（低体力跑得慢，预判要同步变慢，
    // 否则 AI 会误判“跑得到”而漏球）；1-2 级保持固定满速，保留原有的预判误差（“乱”的一部分）
    private double decisionMoveSpeed() {
        return smartEnergy ? effectiveMoveSpeed : GameProperties.moveSpeed;
    }

    // 预设失误回合的跑位打折：滑动窗口武装拍内横移只出 55% 速度——
    // AI 追快球/大角度球时跑位不到位，球够不到自然挥空/漏接；
    // 与出球压角(MatchAimAssist 消费拍)同回合配合，只在单人低难度出现
    private double moveSpeedForThisFrame() {
        return MatchAimAssist.isArmed() && !maxLevel
                ? decisionMoveSpeed() * MISS_MOVE_SCALE
                : decisionMoveSpeed();
    }

    // 无球时的基础站位
    private void offBallPositioning(int side) {
        // 左侧玩家可移动区间中心大约在 260（[91, 429] 中点）
        final double anchorLeft  = 260.0;
        // 右侧玩家可移动区间中心大约在 640（[471, 809] 中点）
        final double anchorRight = 640.0;

        double target = (side == 1) ? anchorLeft : anchorRight;
        // 体力管理：低体力时放大就位死区，站在锚点附近不动，
        // 利用站立回血 20/s 快速恢复（移动回血仅 3/s），避免来回踱步浪费体力
        double deadzone = (smartEnergy && energy < GameProperties.lowEnergyIdleThreshold) ? 30.0 : 2.0;
        if (Math.abs(computerX - target) <= deadzone) {
            isMoveLeft = isMoveRight = false;
            return;
        }
        if (computerX < target) { isMoveRight = true; isMoveLeft = false; }
        else                    { isMoveLeft  = true; isMoveRight = false; }
    }

    private void predictBallLanding() {
        predictedLandingX = preLanding(GameProperties.floorBallY);
        predictedLandingY = GameProperties.floorBallY;
    }

    private boolean isBallComingToMySide(int side) {
        boolean ballInMyArea = (side == 1 && badmintonX < GameProperties.netPosition) ||
                (side == -1 && badmintonX > GameProperties.netPosition);

        boolean ballComingToMe = (side == 1) ? (badmintonSpeedX < 0 || badmintonX < GameProperties.netPosition)
                : (badmintonSpeedX > 0 || badmintonX > GameProperties.netPosition);

        return ballInMyArea && ballComingToMe &&
                badmintonY > GameProperties.netHeight &&
                badmintonY < GameProperties.floorBallY - 30;
    }

    /**
     * 残血扣杀体力门：仅单人非满级的 3/5 级生效——3级玩家体力 ≥20、5级 ≥30 时
     * 不主动扣杀（扣杀留给残血玩家收头，满血只做调动回球）；1/2/4级不设门。
     * 满级(G键)不走此门，联机同轨不破
     */
    private boolean opponentLowEnoughToSmash() {
        if (maxLevel) return true;
        double threshold = switch (profile.level) {
            case 3 -> SMASH_OPPONENT_LOW_ENERGY_L3;
            case 5 -> SMASH_OPPONENT_LOW_ENERGY_L5;
            default -> -1.0; // 其余档不设门
        };
        if (threshold < 0.0) return true;
        return opponentEnergy < threshold;
    }

    private boolean shouldReact() {
        if (maxLevel) return true; // 满级恒反应：消除随机门槛，保证双端决策同轨（不掷接杀门，同轨不破）
        int latchIdx = (traceSide == 1) ? 0 : 1;
        // ①接玩家扣杀：连续"扣杀来球"期间整拍只掷一次——5级60%、4级40%、
        // 1-3级按档递减(28/18/10%)。拒绝后闩锁保持"拒"，本拍 shouldReact 恒 false →
        // AI 站定不反应，视觉即"这记扣杀没接住"；来球不再读作扣杀(减速/击回/落地)即清闩，
        // 下一记扣杀重新掷
        if (isIncomingSmash(traceSide)) {
            if (smashLatch[latchIdx] == 0)
                smashLatch[latchIdx] = random.nextDouble() < profile.smashReceiveChance ? 1 : 2;
            if (smashLatch[latchIdx] == 2) return false;
        } else {
            smashLatch[latchIdx] = 0;
        }
        // ②常规逐帧反应门：菜鸟(1级0.50)每帧只有一半概率过门→平均慢半拍，
        // 5级0.02接近立即反应；网前快球低难度漏接属预期的难度差异
        return random.nextDouble() > profile.reactionGate;
    }

    private void executeStrategy(int side, boolean isShotCooldown) {
        // 对手位置瞬时观察（时间片采样），供下方击球策略与轻重击概率联合判定
        observeOpponent(side, opponentX);

        //杀球防守
        if (handleSmashDefense(side, isShotCooldown)) { rejMark("def"); return; }

        //提前起跳扣杀：高位/网前下落球不等进圈、不退后场等球，先跳(风存后空中截击)。
        //放在网前拦截分支之前：球快落到前场时直接网前跳起拦截扣杀
        if (earlyJumpSmash(side, isShotCooldown)) return;

        // 跳杀风存跟随：起跳后12帧内横移朝球当前x(而非落地点)，防止 AI 被 adjustPosition
        // 带到球的后方——网前跳起位置太靠后、场边高球追不平都是这个原因
        int sideIdx = (side == 1) ? 0 : 1;
        if (jumpSmashFollow[sideIdx] > 0) {
            jumpSmashFollow[sideIdx]--;
            if (isJumpCooldown) {
                rejMark("wf"); // 风存跟随窗口内（已起跳，本帧只跟球）
                moveFollowBallX();
                return; // 风存期只跟球：不出招(风存状态机管挥拍)、不另跳
            }
        }

        //预测拦截
        if (handleFrontCourt(side, isShotCooldown)) { rejMark("fc"); return; }

        //位置调整
        adjustPosition();

        //常规击球
        if (!isShotCooldown && getDistance() < (hitAreaRadius + racketRadius)) {
            decideShotStrategy();
        } else if (traceRej.isEmpty()) {
            rejMark("dcd"); // 未进击球圈或挥拍冷却中：本帧无法出招
        }

        //考虑跳跃（对手远离球网；用绝对值使左右半场 AI 判定对称——
        //左侧 AI 的对手在右半场，旧式 netPosition-opponentX 恒为负导致永不跳跃）
        //水平对齐容差 16→40：旧值要求 AI 几乎正对球才考虑起跳，跳跃触发过少
        if (getDistance() < (100 + hitAreaRadius + racketRadius) &&
                Math.abs(opponentX - GameProperties.netPosition) > 100 &&
                Math.abs(computerX - badmintonX) < 40)
        {
            moveVertical();
        } else if (!isJump && traceRej.isEmpty()) {
            rejMark("jPre"); // 普通跳跃前置未过：距离/对手位置/水平对齐
        }
    }
    //杀球防守
    private boolean handleSmashDefense(int side, boolean isShotCooldown) {
        if (!isIncomingSmash(side)) return false;

        // 目标拦截高度：根据下坠速度自适应，越快越高拦截
        double vy = badmintonSpeedY;
        double offset = clamp(
                map(vy, SMASH_VY_THRESHOLD, 1200.0, SMASH_INTERCEPT_Y_MIN, SMASH_INTERCEPT_Y_MAX),
                SMASH_INTERCEPT_Y_MIN, SMASH_INTERCEPT_Y_MAX
        );
        double targetY = computerY - offset;

        if (badmintonY > targetY) {
            targetY = computerY - Math.max(10.0, SMASH_INTERCEPT_Y_MIN);
        }

        InterceptPrediction pred = predictAtY(targetY);

        // 异常保护：若无法预测，直接对位到当前球X
        double desiredX = Double.isFinite(pred.x) && pred.t > 0 ? pred.x : badmintonX;

        // 在“本方侧 + 少量偏移”处对位，避免贴身打不到
        desiredX += (side == 1 ? -SMASH_CONTACT_OFFSET : SMASH_CONTACT_OFFSET);

        // 边界（人物中心）约束
        double leftBound, rightBound;
        if (side == 1) {
            leftBound  = GameProperties.playFieldLeft + GameProperties.playerWidth / 2.0;
            rightBound = GameProperties.netPosition - GameProperties.playerWidth / 2.0;
        } else {
            leftBound  = GameProperties.netPosition + GameProperties.playerWidth / 2.0;
            rightBound = GameProperties.playFieldRight - GameProperties.playerWidth / 2.0;
        }
        desiredX = clamp(desiredX, leftBound, rightBound);

        // 横移就位
        double dx = desiredX - computerX;
        if (Math.abs(dx) > NET_ALIGN_DEADZONE) {
            if (dx > 0) { isMoveRight = true; isMoveLeft = false; }
            else        { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }

        // 杀球防守默认不跳，减少“起跳接空”
        isJump = false;

        // 进入击球圈：防守回球。原版无脑轻击高远，右侧持续重击时左侧会一直被动挑球，
        // 缺乏反击。改为按体力交替——体力充足时改用重击平抽反击，体力不足才退回防守轻击。
        // 用能量阈值(确定性)而非随机，满级联机双端同轨不分叉
        if (!isShotCooldown && getDistance() < (hitAreaRadius + racketRadius + SMASH_HIT_MARGIN)) {
            isShot = true;
            // 重击反击门槛：够一次重击(35)后仍留 20 余量，避免打完被下一拍掏空；否则防守轻击
            boolean counterHeavy = energy > GameProperties.heavyShotEnergy + 20.0;
            isHeavyhit = counterHeavy;
            isLighthit = !counterHeavy;

            // 防守角度：轻击走高远挑球拉开，重击走平抽压回对手半场
            double base;
            if (counterHeavy) {
                base = (side == 1) ? 330.0 : 210.0; // 平抽，落点偏对方后场
            } else {
                base = (side == 1) ? 300.0 : 240.0; // 高远防守
            }
            superShotAngleDeg = base;
            return true; // 已完成处理
        }

        // 若未进入击球圈，仍保持本分支主导，避免被其它策略打断
        return true;
    }

    private boolean isIncomingSmash(int side) {
        double netX = GameProperties.netPosition;
        // 到我方或即将入我方
        boolean onMySideOrEntering = (side == 1) ? (badmintonX <= netX + 40.0)
                : (badmintonX >= netX - 40.0);
        // 朝我方飞
        boolean comingToMe = (side == 1) ? (badmintonSpeedX < 0) : (badmintonSpeedX > 0);

        // 速度与高度条件
        double speed = getBallSpeed();
        boolean fastDown  = badmintonSpeedY > SMASH_VY_THRESHOLD;
        boolean speedHigh = speed > SMASH_SPEED_THRESHOLD;
        boolean notTooLow = badmintonY < GameProperties.floorBallY - 60.0;

        return onMySideOrEntering && comingToMe && fastDown && speedHigh && notTooLow;
    }
    //杀球防守结束

    // 网前对位
    private boolean handleFrontCourt(int side, boolean isShotCooldown) {
        if (!isFrontCourtScenario(side)) return false;

        // 网带顶Y
        double netTopY = GameProperties.floorBallY - GameProperties.netHeight;
        // 目标拦截高度：把球限制在网带下方 20~90 之间（便于压网/搓球）
        double targetY = clamp(badmintonY, netTopY + 20, netTopY + 90);

        // 预测该高度下的 X
        InterceptPrediction pred = predictAtY(targetY);
        double desiredX = pred.x;

        // 对位在“本方侧 + 安全偏移”
        double offset = Math.max(12.0, hitAreaRadius - NET_SAFE_OFFSET);
        if (side == 1) desiredX -= offset; else desiredX += offset;

        // 边界约束：人物可达边界（人物中心）
        double leftBound, rightBound;
        if (side == 1) {
            leftBound  = GameProperties.playFieldLeft + GameProperties.playerWidth / 2.0;
            rightBound = GameProperties.netPosition - GameProperties.playerWidth / 2.0;
        } else {
            leftBound  = GameProperties.netPosition + GameProperties.playerWidth / 2.0;
            rightBound = GameProperties.playFieldRight - GameProperties.playerWidth / 2.0;
        }
        desiredX = clamp(desiredX, leftBound, rightBound);

        // 就位
        double dx = desiredX - computerX;
        if (Math.abs(dx) > NET_ALIGN_DEADZONE) {
            if (dx > 0) { isMoveRight = true; isMoveLeft = false; }
            else        { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }

        // 网前禁止起跳
        isJump = false;

        // 近距离命中检测 + 轻击
        if (!isShotCooldown && getDistance() < (hitAreaRadius + racketRadius + 8)) {
            isShot = true;
            isLighthit = true;
            isHeavyhit = false;

            //向对角下压角度
            superShotAngleDeg = (side == 1)
                    ? 20.0  + (random.nextDouble() - 0.5) * 12.0
                    : 160.0 + (random.nextDouble() - 0.5) * 12.0;
        }

        return true;
    }

    private boolean isFrontCourtScenario(int side) {
        // 墙反弹冷却内不抢网
        if (isLikelyRecentWallBounce()) return false;

        double netX = GameProperties.netPosition;
        double netTopY = GameProperties.floorBallY - GameProperties.netHeight;
        double clearanceToNetTop = badmintonY - netTopY; // >0 表示低于网带

        boolean nearNetHoriz = Math.abs(badmintonX - netX) <= NET_X_WINDOW;
        boolean nearNetVert  = (clearanceToNetTop > -NET_Y_MARGIN_BELOW) &&
                (clearanceToNetTop < NET_Y_MARGIN_ABOVE);
        boolean onMySideOrEntering =
                (side == 1 && badmintonX <= netX + 12) ||
                        (side == -1 && badmintonX >= netX - 12);

        boolean vyFriendly = Math.abs(badmintonSpeedY) < 750;

        return nearNetHoriz && nearNetVert && onMySideOrEntering && vyFriendly;
    }

    private void adjustPosition() {
        double idealX = predictedLandingX;
        double distance = idealX - computerX;
        double maxMoveDistance = moveSpeedForThisFrame() * GameProperties.frameTime;

        // 旧缩放(d*0.1+0.03)会把停止阈值放大到 50px 以上，低等级触及圈仅约 52px，
        // AI 停在圈边缘外漏球、显得站位呆；改为线性缩放：1级0.75、2级0.9、3级0.95、4/5级1.0，
        // 站位始终收敛到触及圈内，满级(difficultyLevel=1.0)行为与旧版完全一致，联机不分叉。
        // （原 if(difficultyLevel==1) 分支与该公式在系数1.0处取值相同，已合并）
        distance *= (0.5 + 0.5 * difficultyLevel);

        if (Math.abs(distance) > maxMoveDistance) {
            if (distance > 0) { isMoveRight = true; isMoveLeft = false; }
            else               { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }
    }

    private void decideShotStrategy() {
        isShot = true;

        double opponentDistance = Math.abs(opponentX - computerX);
        double ballHeight = badmintonY;
        double ballSpeed = getBallSpeed();

        boolean canSmash =
                ballHeight < (computerY - 20) &&
                        ballHeight > (computerY - 120) &&
                        getDistance() < (hitAreaRadius + racketRadius) &&
                        Math.abs(badmintonSpeedY) < 500;

        if (canSmash && (!smartEnergy || badmintonSpeedY >= 0)) {
            if (!smartEnergy) {
                // 1-2级圈内扣杀走档案门槛：菜鸟(1级) 55%、普通(2级) 80%——
                // 其余落穿到下方常规回球分支，两档扣杀积极性不再雷同
                if (varianceRoll() < profile.lowSmashGate) {
                    moveHorizontal(200);
                    superShot();
                    return;
                }
                if (MissTrace.isEnabled())
                    rejMark(String.format("lSmash(need%.2f)", profile.lowSmashGate)); // 实参先于早退求值，须外层守卫
            }
            // 高难度：几何判定能过网才扣杀；压不过网时落穿到下方常规分支挑/抽过网，
            // 避免扣杀直接砸网或砸在自己半场
            if (preciseSmash()) return;
        }

        // 前场跳扣：球在本方前半场(离网190px内)、位置高、水平速度慢（对方放网/搓球
        // 回过来的高挂球与高慢球）——这类球下坠慢、有充分起跳时间，是最该下压终结的
        // 一拍。复用 preciseSmash 的过网仿真与跳/站决策：正上方高慢球由其内部的
        // 接触几何重算压成跳杀，压不过落穿到常规分支。
        // 仅高难度启用(体力管理路径)，1-2级保持原有简单行为
        if (smartEnergy
                && Math.abs(badmintonX - GameProperties.netPosition) < FRONT_COURT_WINDOW
                && badmintonY < computerY - GameProperties.smashJumpHeight
                && Math.abs(badmintonSpeedX) < 150.0
                && badmintonSpeedY < 200.0) {
            if (preciseSmash()) return;
        }

        // 概率扣杀（圈内路径）：按扣杀风格分层——3级原概率(中前场60%/后场35%)、
        // 4级激进(85%/55%，多扣)、5级与满级精准(40%/20%，少扣但每次必过网成功率高)；
        // 其内含切线角过网仿真+体力/跳跃冷却/可达门槛，不合适自动返回 false
        // 落穿到下方调动分支；varianceRoll 双端同拍不分叉。
        // 诊断：概率门失败记 smProb(need,roll)=本拍故意不扣（仍会 variedShot 正常回球），
        // 精确重构保持原短路语义与 varianceRoll 恰好一次调用（随机流不偏移）
        if (smartEnergy && energy > GameProperties.jumpEnergy + GameProperties.heavyShotEnergy) {
            double need = Math.abs(badmintonX - GameProperties.netPosition) > SMASH_COURT_WINDOW
                    ? smashProbBack() : smashProbMid();
            double roll = varianceRoll();
            if (roll < need && preciseSmash()) {
                return;
            }
            if (roll >= need) rejMark(String.format("smProb(need%.2f,roll%.2f)", need, roll));
        }

        // “对手/自己离网多远”一律用绝对值：旧式 netPosition-x 只在 AI 在右半场时为正，
        // 左侧 AI（对手在右）恒为负 → 分支判断全部失真，永远只会出高球轻击
        double opponentNetDist = Math.abs(opponentX - GameProperties.netPosition);
        double computerNetDist = Math.abs(computerX - GameProperties.netPosition);
        // 叶子统一走 variedShot：原局势偏好(高远/平抽)作稳妥拍，20% 反着来增加球路变化
        if (opponentDistance > 400 && opponentNetDist < defenceOpponentDistance) {
            if (ballSpeed > 800) {
                moveHorizontal(hitAreaRadius / 1.414);
                variedShot(false);
            } else {
                moveHorizontal(-hitAreaRadius);
                variedShot(true);
            }
        } else if (opponentDistance > 200 && opponentNetDist < defenceOpponentDistance) {
            if (computerNetDist < highShotDistance) {
                moveHorizontal(-hitAreaRadius);
                variedShot(true);
            } else {
                moveHorizontal(GameProperties.hitAreaCenterHeight / 1.5);
                variedShot(false);
            }
        } else {
            if (computerNetDist < 200 || opponentNetDist < defenceOpponentDistance) {
                moveHorizontal(-hitAreaRadius);
                variedShot(true);
            } else if (ballSpeed > 600) {
                moveHorizontal(GameProperties.hitAreaCenterHeight / 1.2);
                variedShot(false);
            } else {
                moveHorizontal(-GameProperties.hitAreaCenterHeight / 1.5);
                variedShot(true);
            }
        }
    }

    // 回球变拍：体力充沛时按"策略(对手位置观察) + 基础轻重概率"联合判定，
    // 观察给倾向、概率给不确定性——相同球况不再永远回同一拍
    private void variedShot(boolean preferHigh) {
        // 体力不充沛(≤50，即再打两次重击就见底)时强制轻击高远：省下重击体力，
        // 同时把对手压向底线、下一拍再放前场，用前后跑动消耗对手体力
        if (smartEnergy && energy <= GameProperties.heavyShotEnergy + 15.0) {
            highShot();
            return;
        }
        // 策略+轻重概率联合判定：基础概率(后场65%重/中前场35%重)先立底，
        // 再按时间片观察的对手占位与移动趋势修正，最后叠局势偏好 ±0.10，
        // 与随机 roll 一次性决定轻重——观察是倾向，概率保证不确定性
        double heavyProb = Math.abs(computerX - GameProperties.netPosition) > SMASH_COURT_WINDOW
                ? 0.65 : 0.35;
        if (!Double.isNaN(observedOppNetDist)) {
            if (observedOppNetDist <= OBS_FRONT_NET) {
                heavyProb += 0.25; // 对手压在前场：重击平抽穿透
            } else if (observedOppNetDist > OBS_BACK_NET) {
                heavyProb -= 0.15; // 对手退守后场：轻击高远继续把他钉深
            }
            if (observedOppRushNet) heavyProb -= 0.15;   // 对手急冲网前：轻吊让其急停回跑
            if (observedOppFallBack) heavyProb += 0.10;  // 对手正在回退：重击追身压底线
        }
        heavyProb += preferHigh ? -0.10 : 0.10;
        // 3级"诱饵"档案：重击倾向 ×0.4 —— 多出高远把进攻权交给玩家，
        // 自己靠体力管理+高跳跃概率专注接球；扣杀失误由免过网兜底天然放大
        heavyProb *= profile.heavyScale;
        heavyProb = clamp(0.05, 0.9, heavyProb);
        double roll = varianceRoll();
        if (roll < heavyProb) middleShot(); else highShot();
    }

    // 对手位置瞬时观察：每 OBSERVE_INTERVAL 帧在时间片上记录一次对手位置，
    // 时间片内取值恒定（不逐帧跟踪，拍型不随对手微晃抖动），相邻两片连线得移动趋势
    private void observeOpponent(int side, double opponentX) {
        int i = (side == 1) ? 0 : 1;
        if (Double.isNaN(oppSampleLast[i]) || oppSampleCd[i] <= 0) {
            oppSamplePrev[i] = Double.isNaN(oppSampleLast[i]) ? opponentX : oppSampleLast[i];
            oppSampleLast[i] = opponentX;
            oppSampleCd[i] = OBSERVE_INTERVAL;
        } else {
            oppSampleCd[i]--;
        }
        double last = oppSampleLast[i];
        observedOppNetDist = Math.abs(last - GameProperties.netPosition);
        double delta = last - oppSamplePrev[i];
        double toNet = Math.signum(GameProperties.netPosition - last);
        boolean moved = Math.abs(delta) >= OBS_TREND_MIN;
        observedOppRushNet = moved && Math.signum(delta) == toNet;
        observedOppFallBack = moved && Math.signum(delta) == -toNet;
    }

    // 观察结果只读口（联调/测试用）：对手离网距离为时间片采样值，NaN=尚无采样
    public double observedOppNetDist() { return observedOppNetDist; }
    public boolean observedOppRushNet() { return observedOppRushNet; }
    public boolean observedOppFallBack() { return observedOppFallBack; }

    // [0,1) 伪随机源。满级(maxLevel)联机时必须双端同轨，不能用 Random——
    // 改由球心坐标导出：双端球轨迹经固定步长物理回放逐位一致(StrictMath)，
    // 同一帧 badmintonX/Y 相同 → 此值相同 → 双端选同一拍，不分叉。
    // 非满级仅单人存在，直接用随机即可
    private double varianceRoll() {
        if (!maxLevel) return random.nextDouble();
        double h = StrictMath.sin(badmintonX * 12.9898 + badmintonY * 78.233) * 43758.5453;
        return h - StrictMath.floor(h);
    }

    // 球相对肩点的挥拍切线出球角(0~360)：右半场=极角-90，左半场=极角+90。
    // 实际出球角由挥拍扫掠相位决定，决策层按当前几何预期
    private double tangentHitAngle() {
        double alpha = StrictMath.toDegrees(StrictMath.atan2(badmintonY - computerY, badmintonX - computerX));
        if (alpha < 0) alpha += 360;
        boolean isRightSide = computerX > GameProperties.netPosition;
        double angle = isRightSide ? alpha - 90 : alpha + 90;
        if (angle < 0) angle += 360;
        else if (angle >= 360) angle -= 360;
        return angle;
    }

    // 提前起跳扣杀：球还在高位(或网前拦截位)下落时提前起跳，风存6帧后空中截击。
    // 等球落进105px击球圈再决策时 vy 常已>400，起跳可达性推演(keepsContact)必拒，
    // AI 只能站着轻挡或退后场等球——"先跳再击球"的主路径。
    // 仅高难度(smartEnergy)启用；体力门槛(>55)天然限频：两次跳杀即见底，靠站桩回血续航
    private boolean earlyJumpSmash(int side, boolean isShotCooldown) {
        if (!smartEnergy || isShotCooldown || isJumpCooldown) {
            if (MissTrace.isEnabled()) {
                if (!smartEnergy) rejMark("eOff");
                else if (isShotCooldown) rejMark("eCd");
                else rejMark("eJcd");
            }
            return false;
        }
        if (energy <= GameProperties.jumpEnergy + GameProperties.heavyShotEnergy) { rejMark("eEne"); return false; }
        // 残血扣杀门(3/5级)：玩家没残血不提前跳扣，落穿常规回球
        if (!opponentLowEnoughToSmash()) { rejMark("eOppE"); return false; }
        double netDist = Math.abs(badmintonX - GameProperties.netPosition);
        if (badmintonSpeedY <= 0) { rejMark("eUp"); return false; } // 只接下落球，上升球交给常规分支
        // 高度门槛：网前拦截位放宽(球快落到前场也跳)，其余按高位球标准
        double minHeight = netDist <= FRONT_COURT_WINDOW ? EARLY_SMASH_MIN_H_NET : EARLY_SMASH_MIN_H;
        if (computerY - badmintonY < minHeight) { rejMark("eH"); return false; }
        if (Math.abs(computerX - badmintonX) > EARLY_SMASH_ALIGN) { rejMark("eAl"); return false; }
        double angle = tangentHitAngle();
        if (angle >= 180) { rejMark("eAng"); return false; } // 切线朝上（球在肩后/正上方）不提前跳
        double lagMargin = Math.min(SMASH_LAG_MARGIN_CAP, Math.max(0.0, badmintonSpeedY * 0.1));
        // 与 preciseSmash 同款调平循环：单角对远球会被空气阻力拖到过网前先落地，
        // 每次向本方前上方调5°(右半场+5、左半场-5)直到过网，压平超上限说明本质不是扣杀球
        double flattenStep = (computerX > GameProperties.netPosition) ? 5.0 : -5.0;
        boolean clears = false;
        for (double tuned = 0; tuned <= SMASH_FLATTEN_MAX_DEG + 1e-9; tuned += 5.0) {
            if (Badminton.launchClearsNet(badmintonX, badmintonY, angle + flattenStep * tuned, 2500.0, lagMargin)) {
                clears = true;
                break;
            }
        }
        if (!clears) { rejMark("eNet"); return false; }
        if (!earlySmashConnectable(side)) { rejMark("eCon"); return false; } // 风存+挥拍窗口内接不到球不白跳
        // 分层概率：网前拦截必跳；中后场按扣杀风格取概率(3级原概率/4级激进/5级与满级精准)；
        // varianceRoll 用球心坐标哈希，满级联机双端同拍不分叉
        if (netDist > FRONT_COURT_WINDOW) {
            double prob = (netDist <= SMASH_COURT_WINDOW) ? earlyProbMid() : earlyProbBack();
            double roll = varianceRoll();
            if (roll >= prob) { // 概率层故意不跳（诊断关键证据：need/roll 显式入档）
                rejMark(String.format("eProb(need%.2f,roll%.2f)", prob, roll));
                return false;
            }
        }
        moveFollowBallX(); // 触发帧就朝球当前x走(不用preLanding落点：落点常在球后方)
        jumpSmashFollow[(side == 1) ? 0 : 1] = 12; // 开风存跟随窗口，覆盖风存6帧+挥拍
        isShot = true;
        isHeavyhit = true;
        isLighthit = false;
        isJump = true;
        isSmashIntent = true;
        return true;
    }

    // 圈内/提前扣杀概率：统一取当前档档案（档案按档声明，无风格 switch）
    private double smashProbMid() { return profile.inRingMid; }

    private double smashProbBack() { return profile.inRingBack; }

    private double earlyProbMid() { return profile.earlyMid; }

    private double earlyProbBack() { return profile.earlyBack; }

    // 跳杀风存跟随专用：朝球当前位置横移，边界钳制与 moveHorizontal 同公式
    private void moveFollowBallX() {
        double distance = badmintonX - computerX;
        double maxMoveDistance = moveSpeedForThisFrame() * GameProperties.frameTime;
        double leftBoundary = GameProperties.playFieldLeft + GameProperties.playerWidth / 2;
        double rightBoundary = GameProperties.playFieldRight - GameProperties.playerWidth / 2;
        if (computerX < leftBoundary) {
            isMoveRight = true; isMoveLeft = false;
        } else if (computerX > rightBoundary) {
            isMoveLeft = true; isMoveRight = false;
        } else if (Math.abs(distance) > maxMoveDistance) {
            if (distance > 0) { isMoveRight = true; isMoveLeft = false; }
            else { isMoveLeft = true; isMoveRight = false; }
        } else {
            isMoveLeft = false; isMoveRight = false;
        }
    }

    // 挥拍首段相位扫掠距离：swingF=挥拍第几帧(1起)，复刻 StickMan 上/下方两套扫掠
    // 相位与下方击球的区间门，返回球心到"拍轴→拍头"线段的最短距离(同 racketSweepHit
    // 口径)；命中窗口外或球越网后返回 +∞。供提前跳杀(判≤racket)与场边严判(判≤racket*安全系数)
    // 两处推演共用，保证"决策层预判"与"真实挥拍"用同一套几何，不再出现推演比真实宽的误判
    private double swingSweepDist(int swingF, int side, double pivotX, double shoulderY,
                                  double ballX, double ballY, boolean useDown) {
        double deltaT = swingF / 60.0; // 与原式 (f-7)/60.0 逐字一致，保 0.15s 边界判定不变
        double animTime = useDown ? GameProperties.shotAnimationTime2 : GameProperties.shotAnimationTime;
        if (deltaT >= animTime) return Double.MAX_VALUE; // 命中判定仅各自动画首段
        double nowAngle;
        if (!useDown) {
            // StickMan 上方击球相位：起手180+45，0.15s内扫至300+45，side=-1镜像
            nowAngle = 180 + deltaT
                    * ((180 + 45 + GameProperties.hitAreaAngleUp - 180) / GameProperties.shotAnimationTime) + 45;
            if (side == -1) nowAngle = 180 - nowAngle;
        } else {
            // StickMan 下方击球相位：targetAngle=180-145-hitAreaAngleDown，0.17s扫掠
            double targetAngle = 180 - 145 - GameProperties.hitAreaAngleDown;
            nowAngle = 180 + deltaT * ((targetAngle - 180) / GameProperties.shotAnimationTime2);
            nowAngle += 45;
            if (side == -1) nowAngle = 180 - nowAngle;
        }
        while (nowAngle < 0) nowAngle += 360;
        while (nowAngle > 360) nowAngle -= 360;
        // 下方击球还有相位区间门(区间外整个判定跳过，不算命中)
        if (useDown && !((side == 1 && nowAngle < 110) || (side == -1 && nowAngle > 70)))
            return Double.MAX_VALUE;
        double racketX = pivotX + StrictMath.cos(StrictMath.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
        double racketY = shoulderY + StrictMath.sin(StrictMath.toRadians(nowAngle)) * GameProperties.hitAreaRadius;
        // 真实判定还要求球在本方半场（越网后挥拍不再命中），否则推演会高估
        boolean onMyHalf = (ballX - GameProperties.netPosition) * side <= 0;
        if (!onMyHalf) return Double.MAX_VALUE;
        // 拍面扫掠判定(与 StickMan.racketSweepHit 同口径)：球心到拍轴→拍头线段
        // 最短距离≤racket，把拍杆扫过区都算命中，而非只查拍头一个点
        double segX = racketX - pivotX, segY = racketY - shoulderY;
        double lenSq = segX * segX + segY * segY;
        double t = lenSq == 0 ? 0 : ((ballX - pivotX) * segX + (ballY - shoulderY) * segY) / lenSq;
        if (t < 0) t = 0; else if (t > 1) t = 1;
        double nearX = pivotX + segX * t, nearY = shoulderY + segY * t;
        // 线段扫掠距离(贴身兜底是 pivot 距离≤racket，两者取小等价于"拍杆扫过区命中")
        double sweep = StrictMath.sqrt((ballX - nearX) * (ballX - nearX)
                + (ballY - nearY) * (ballY - nearY));
        double pivot = StrictMath.sqrt((ballX - pivotX) * (ballX - pivotX)
                + (ballY - shoulderY) * (ballY - shoulderY));
        return Math.min(sweep, pivot);
    }

    // 挥拍首段相位扫掠命中判定：球心距拍杆扫掠 ≤ racketRadius 即算命中(真实口径)
    private boolean swingPhaseHit(int swingF, int side, double pivotX, double shoulderY,
                                  double ballX, double ballY, boolean useDown) {
        return swingSweepDist(swingF, side, pivotX, shoulderY, ballX, ballY, useDown)
                <= GameProperties.racketRadius;
    }

    // 推演公共物理步：球受空气阻力(先Δv后位移，与 Badminton.onUpdate 一致)、
    // 贴墙0.6反弹、AI 中心横移收差并被空气墙钳制——三处缺一都会让预判与真实脱节。
    // 数组为推演状态[ballX,ballY,ballVx,ballVy,shoulderY,pivotX,jumpVy]，原地更新不新建。
    // airborne=false 时肩静止（站定拍肩膀不移动）且不推进垂直速度；
    // airborne=true 走起跳推进（风存拍/决策层跳杀推演）
    private static void stepPhysics(double[] st, double step, boolean airborne) {
        double jumpVy = st[6];
        double shoulderY = st[4];
        if (airborne) {
            jumpVy += GameProperties.jumpGravity;
            shoulderY += jumpVy * GameProperties.frameTime;
        }
        double ballVx = st[2], ballVy = st[3];
        double sp = StrictMath.sqrt(ballVx * ballVx + ballVy * ballVy);
        double air = sp == 0 ? 0 : 0.00001 * sp * sp;
        ballVx -= 2.7 * air * (ballVx / sp);
        ballVy += GameProperties.badmintonGravity - 0.5 * air * (ballVy / sp);
        double ballY = st[1] + ballVy * GameProperties.frameTime;
        double ballX = st[0] + ballVx * GameProperties.frameTime;
        // 球心贴图半宽：Badminton 左上角 x 撞墙时球心 = 墙 + 10.5（与 setCenterPosition 一致）
        final double ballHalf = 10.5;
        if (ballX <= GameProperties.playFieldLeft + ballHalf) {
            ballX = GameProperties.playFieldLeft + ballHalf;
            ballVx = -ballVx * 0.6;
        } else if (ballX >= GameProperties.playFieldRight + ballHalf) {
            ballX = GameProperties.playFieldRight + ballHalf;
            ballVx = -ballVx * 0.6;
        }
        double gap = ballX - st[5];
        double pivotX;
        if (Math.abs(gap) > step) pivotX = st[5] + Math.signum(gap) * step;
        else pivotX = ballX;
        // AI 被空气墙卡住：贴边时追不平的那截水平差是真实存在的
        double minPivot = GameProperties.playFieldLeft + GameProperties.playerWidth / 2.0;
        double maxPivot = GameProperties.playFieldRight - GameProperties.playerWidth / 2.0;
        if (pivotX < minPivot) pivotX = minPivot;
        else if (pivotX > maxPivot) pivotX = maxPivot;
        st[0] = ballX; st[1] = ballY; st[2] = ballVx; st[3] = ballVy;
        st[4] = shoulderY; st[5] = pivotX; st[6] = jumpVy;
    }

    // 起跳推进版（决策层跳杀推演用）
    private static void stepPhysics(double[] st, double step) {
        stepPhysics(st, step, true);
    }

    // 提前跳杀可达性：风存6帧+起手1帧后进入挥拍命中判定，逐帧推演并**复刻 StickMan
    // 的真实命中公式**（swingPhaseHit）才算截击成功。
    // 帧对齐(2026-10-15 P2 失分实证)：真实时序=决策帧起跳(同帧扣20)→风存6帧→
    // 决策+6帧起手(拍型此刻定、再扣35)→决策+7帧首接触(deltaT=1/60)。
    // 旧推演 useDown 定于 f=8、命中从 f=8 起——比真实晚1~2帧；且收差步长用
    // 决策时刻满速(250)，而真实风存期体力已掉出满速区间(54→34→19 → 227→171px/s)，
    // 两处偏差让推演多算约6px收差 → 乐观误判"能打到" → 起跳挥空+0.5s冷却，
    // 冷却恰好挡住球真正进入可接带的时刻(P2: 球落地f1170、冷却到f1169)
    private boolean earlySmashConnectable(int side) {
        double shoulderY = computerY;
        double pivotX = computerX;
        double jumpVy = -GameProperties.jumpSpeedY;
        double ballX = badmintonX, ballY = badmintonY;
        double ballVx = badmintonSpeedX, ballVy = badmintonSpeedY;
        // 收差步长按真实体力消耗分段：风存期只扣了跳(20)，起手帧再扣重击(35)。
        // 仅体力管理路径分段（1-2级保留满速推演的原"乱"误差，行为不变）
        double windupStep, swingStep;
        if (smartEnergy && !GameProperties.infiniteEnergyMode) {
            windupStep = moveSpeedAfterDrain(energy - GameProperties.jumpEnergy) * GameProperties.frameTime;
            swingStep = moveSpeedAfterDrain(energy - GameProperties.jumpEnergy - GameProperties.heavyShotEnergy)
                    * GameProperties.frameTime;
        } else {
            windupStep = swingStep = decisionMoveSpeed() * GameProperties.frameTime;
        }
        // 出招帧确定的拍型：真实逻辑在起手帧(决策+6)按球位选上/下方击球(球低于肩上46px
        // 且在本方半场 → 下方击球)，两套相位完全不同，必须跟着真实走
        boolean useDown = false;
        double[] st = {ballX, ballY, ballVx, ballVy, shoulderY, pivotX, jumpVy};
        for (int f = 1; f <= 17; f++) {
            stepPhysics(st, f <= 6 ? windupStep : swingStep);
            ballX = st[0]; ballY = st[1]; ballVx = st[2]; ballVy = st[3];
            shoulderY = st[4]; pivotX = st[5]; jumpVy = st[6];
            if (f < 7 && ballLanded(ballY, ballVy)) return false; // 风存期球已落地，无从截击
            if (f == 6) {
                // 拍型在真实起手帧(决策+6)按当帧球位选定
                useDown = (ballX - GameProperties.netPosition) * side <= 0
                        && ballY > shoulderY - 46; // 真实条件: 球Y > getCenterY()-30 即肩上46px以下
            }
            if (f < 7) continue; // 首接触帧=决策+7(起手后第1帧, deltaT=1/60)
            if (swingPhaseHit(f - 6, side, pivotX, shoulderY, ballX, ballY, useDown)) return true;
        }
        return false;
    }

    // 出招后的真实移动速度：按 StickMan.getEffectiveMoveSpeed 同款体力插值，
    // 输入 e 为该阶段扣除体力后的剩余量（跳-20、再重击-35）
    private static double moveSpeedAfterDrain(double e) {
        if (GameProperties.infiniteEnergyMode) return GameProperties.moveSpeed;
        return e > GameProperties.fullSpeedEnergy ? GameProperties.moveSpeed
                : (GameProperties.moveSpeed0 + (GameProperties.moveSpeed - GameProperties.moveSpeed0)
                        * e / GameProperties.fullSpeedEnergy);
    }

    /**
     * 挥拍起手校验（StickMan 挥拍起手帧调用）：用当帧真实状态预演本拍接触窗口
     * 能否扫到球（与 earlySmashConnectable 同相位几何，起点=当帧、拍型当帧定、
     * 收差用本拍扣完体力后的移速）。返回 false = 明确打不到 → 不起手——
     * 避免白挥后 0.5s 冷却挡住随后真正可接的时刻（2026-10-15 P2 失分根因：
     * 推演晚1~2帧+满速乐观 → 挥空 → 冷却到 f1169 而球 f1170 落地）。
     * 起手后球进窗口自然重试，不会"站定不接"。
     *
     * @param heavy  本拍是否重击（定体力扣减后的收差步长）
     * @param jumpVy0 当前垂直速度：风存拍=起跳6帧后(-jumpSpeedY+6*gravity)；
     *                站定拍=0。肩静止近似对站定拍精确，对风存拍保守偏低（拒了转普通拍无害）
     */
    public boolean connectableThisSwing(boolean heavy, double jumpVy0) {
        if (traceSide == 0) return true; // 默认构造(无状态)不拦
        // 球已过本方半场：真实命中判定同样要求 (x-net)*side<=0，必打不到
        if ((badmintonX - GameProperties.netPosition) * traceSide > 0) return false;
        double postEnergy = energy - (heavy ? GameProperties.heavyShotEnergy : GameProperties.lightShotEnergy);
        double step = moveSpeedAfterDrain(postEnergy) * GameProperties.frameTime;
        // 拍型当帧（真实 shotType：球越网 <0 且球在 cy-30 之下 = 肩上46px以下）
        boolean useDown = (badmintonX - GameProperties.netPosition) * traceSide < 0
                && badmintonY > computerY - 46;
        double[] st = {badmintonX, badmintonY, badmintonSpeedX, badmintonSpeedY,
                computerY, computerX, jumpVy0};
        boolean airborne = jumpVy0 != 0; // 站定拍肩静止；风存拍按当前垂直速度继续推进
        for (int f = 1; f <= 17; f++) {
            stepPhysics(st, step, airborne);
            if (ballLanded(st[1], st[3])) return false; // 推演中球已落地：之后的命中都是假的
            if (swingPhaseHit(f, traceSide, st[5], st[4], st[0], st[1], useDown)) return true;
        }
        return false;
    }

    // 推演终止判据：与 Badminton.onUpdate 落地判定同口径 nextY = y+vy*frameTime >= floorBallY
    private static boolean ballLanded(double ballY, double ballVy) {
        return ballY + ballVy * GameProperties.frameTime >= GameProperties.floorBallY;
    }

    // 高难度扣杀智能（圈内路径）：球已落进击球圈时按挥拍切线预期出球角做物理过网仿真，
    // 满足跳杀条件才起跳扣——接触延迟的下落由 SMASH_LAG_MARGIN 放宽兜住，
    // StickMan 接触层安全网再兜一道
    // 返回 true = 已执行跳杀；false = 压不过网/跳不了，调用方落穿到常规挑/抽分支
    // （扣杀必须先跳再击球，不再有站立重扣路径；高位球的主路径是 earlyJumpSmash 提前跳）
    private boolean preciseSmash() {
        // 残血扣杀门(3/5级)：玩家没残血不圈内扣杀，返回 false 落穿下方调动分支
        if (!opponentLowEnoughToSmash()) { rejMark("prOppE"); return false; }
        double heightAbove = computerY - badmintonY; // 球高出肩部的像素
        double expectedAngle = tangentHitAngle();
        boolean isRightSide = computerX > GameProperties.netPosition;
        // 出球角不在扣球半圈（≥180° 是高球，球在肩后/正上方）。
        // 高慢球补救：球在本方前半场且慢速下坠时，接触时刻(~0.1s后)球会下落一段、
        // 而 AI 跳杀会把肩点抬高60px并站到球的后下方(保证球在肩前上方)——
        // 按"抬肩60px + 站位后挪40px"的接触几何重算角度，重算后仍≥180° 才放弃。
        // 仅此窄场景启用，常规扣杀路径几何不变
        if (expectedAngle >= 180) {
            boolean highSlowBall = Math.abs(badmintonX - GameProperties.netPosition) < FRONT_COURT_WINDOW
                    && Math.abs(badmintonSpeedX) < 150.0
                    && badmintonSpeedY < 200.0
                    && badmintonY < computerY - GameProperties.hitAreaRadius;
            if (!highSlowBall) { rejMark("prAng"); return false; }
            // 后挪方向：右半场 AI 自己在右侧(+x)，站到球右边让球落在肩前上方
            double standX = computerX + (isRightSide ? 40.0 : -40.0);
            double alphaContact = StrictMath.toDegrees(StrictMath.atan2(
                    badmintonY - (computerY - JUMP_RAISE_PX), badmintonX - standX));
            if (alphaContact < 0) alphaContact += 360;
            double contactAngle = isRightSide ? alphaContact - 90 : alphaContact + 90;
            if (contactAngle < 0) contactAngle += 360;
            else if (contactAngle >= 360) contactAngle -= 360;
            if (contactAngle >= 180) { rejMark("prAng"); return false; }
            expectedAngle = contactAngle;
        }
        // 切线角天然比"能过网的角度"更下压（球在肩前下方时斜率≈|dx/dy|），
        // 直接拿它仿真会把正常扣杀全拒掉（表现为 AI 不跳跃、不重击）。
        // 与 StickMan 接触层安全网同向：每次向本方前上方调 5°（右半场 +5、左半场 -5，
        // 绕360°均朝 270° 收敛），能过网即可扣杀；压平超过上限说明本质不是扣杀球。
        // 过网边距按决策到接触（~0.1s）内球的下落量动态计算，而非固定值
        double lagMargin = Math.min(SMASH_LAG_MARGIN_CAP, Math.max(0.0, badmintonSpeedY * 0.1));
        double flattenStep = isRightSide ? 5.0 : -5.0;
        boolean clearsNet = false;
        // 2500 是 angle<180 的 heavyHit 固定档
        for (double tuned = 0; tuned <= SMASH_FLATTEN_MAX_DEG + 1e-9; tuned += 5.0) {
            double candidate = expectedAngle + flattenStep * tuned;
            if (Badminton.launchClearsNet(badmintonX, badmintonY, candidate, 2500.0, lagMargin)) {
                clearsNet = true;
                break;
            }
        }
        if (!clearsNet) { rejMark("prNet"); return false; }
        // 跳杀总价 = 跳20 + 重击35 = 55。条件不满足（体力不足/球不够高/跳跃冷却中/
        // 起跳后撑不到挥拍弧）就不扣，返回 false 落穿到常规挑/抽——
        // 扣杀必须先跳再击球，严禁原地站立扣杀
        if (!(energy > GameProperties.jumpEnergy + GameProperties.heavyShotEnergy)) { rejMark("prEne"); return false; }
        if (isJumpCooldown) { rejMark("prJcd"); return false; }
        if (heightAbove < GameProperties.smashJumpHeight) { rejMark("prH"); return false; }
        if (!smashJumpKeepsContact()) { rejMark("prKeeps"); return false; }
        moveHorizontal(200);
        superShot();
        return true;
    }

    // 跳杀可达性终检：起跳初速700px/s，0.1s内肩膀抬高约60px，而发球类快坠球（vy≈300）
    // 同期下落约34px，肩膀会反超球——球落到肩膀下方后，上方挥拍扫掠弧（只扫肩上扇形）
    // 整个动画窗口都够不到球，起跳接空还白耗25体力。
    // 按 StickMan/Badminton 的逐帧语义（重力逐帧累加、不乘dt）推演接触时刻：
    // 球仍在起跳后的肩膀上方才跳杀，否则降级站立重扣（肩膀不动，球下坠进圈照打）
    private boolean smashJumpKeepsContact() {
        double shoulderY = computerY;
        double jumpVy = -GameProperties.jumpSpeedY;
        double ballY = badmintonY;
        double ballVy = badmintonSpeedY;
        // 决策到接触约 0.1s = 6 帧（挥拍动画 0.15s 的前中段）
        int contactFrames = 6;
        for (int i = 0; i < contactFrames; i++) {
            jumpVy += GameProperties.jumpGravity;
            shoulderY += jumpVy * GameProperties.frameTime;
            ballVy += GameProperties.badmintonGravity;
            ballY += ballVy * GameProperties.frameTime;
        }
        return ballY <= shoulderY;
    }

    // 预判球飞到目标高度 targetY 时的水平位置（y 轴向下）
    // 旧版循环条件方向写反：球在目标上方(下坠场景)时一次都不模拟，拿到的是当前球X；
    // 球在目标下方时又空转 300 步模拟“永远到不了”的轨迹，返回随速度漂移的假落点，
    // AI 跟着假目标反向跑——这正是移动方向偶尔变反的根因
    public double preLanding(double targetY) {
        double vy = badmintonSpeedY;
        double vx = badmintonSpeedX;
        double x = badmintonX;
        double y = badmintonY;

        // 球已在目标高度或更低且不再上升 → 永远不会再穿过目标，直接返回当前 X
        if (y >= targetY && vy >= 0) return x;

        double g = GameProperties.badmintonGravity;
        double dt = GameProperties.frameTime;
        int steps = 0, maxSteps = 300;
        double prevY = y;

        while (steps < maxSteps) {
            // 与 Badminton.onUpdate 逐步物理一致：阻力与重力按步施加，不乘 dt
            double speed = Math.sqrt(vx * vx + vy * vy);
            if (speed == 0) {
                vy += g;
            } else {
                double air = 0.00001 * (speed * speed);
                vx -= 2.7 * air * (vx / speed);
                vy += g - 0.5 * air * (vy / speed);
            }

            // 与球一致的空气墙反弹(0.6 恢复系数)：不模拟反弹会让预测点飞到墙外，AI 追着墙外目标反跑
            double nextX = x + vx * dt;
            if (nextX <= GameProperties.playFieldLeft || nextX >= GameProperties.playFieldRight) {
                double leftSq = (x - GameProperties.playFieldLeft) * (x - GameProperties.playFieldLeft);
                double rightSq = (GameProperties.playFieldRight - x) * (GameProperties.playFieldRight - x);
                x = leftSq < rightSq ? GameProperties.playFieldLeft : GameProperties.playFieldRight;
                vx = -vx * 0.6;
            }
            x += vx * dt;
            y += vy * dt;

            // 路径穿过目标高度即返回（同时兼容目标在球上方/下方两种场景）
            if ((prevY - targetY) * (y - targetY) <= 0) break;
            prevY = y;
            steps++;
        }
        return x;
    }

    // 水平移动到某一目标击球高度对应的X
    public void moveHorizontal(double targetHitOffset) {
        double targetY = computerY - targetHitOffset;
        double idealHitX = preLanding(targetY);

        double distance = idealHitX - computerX;
        double maxMoveDistance = moveSpeedForThisFrame() * GameProperties.frameTime;

        // 低难度也留一定精度
        if (random.nextDouble() > difficultyLevel) {
            distance *= (0.75 + random.nextDouble() * 0.2);
        }

        double leftBoundary = GameProperties.playFieldLeft + GameProperties.playerWidth / 2;
        double rightBoundary = GameProperties.playFieldRight - GameProperties.playerWidth / 2;

        if (computerX < leftBoundary) {
            isMoveRight = true;  isMoveLeft = false;
        } else if (computerX > rightBoundary) {
            isMoveLeft  = true;  isMoveRight = false;
        } else if (Math.abs(distance) > maxMoveDistance) {
            if (distance > 0) { isMoveRight = true; isMoveLeft = false; }
            else              { isMoveLeft  = true; isMoveRight = false; }
        } else {
            isMoveLeft = isMoveRight = false;
        }
    }

    // 垂直跳跃
    public void moveVertical() {
        if (isJump || isJumpCooldown) return;
        if (isLikelyRecentWallBounce()) return;

        double netX = GameProperties.netPosition;
        double vx = badmintonSpeedX;
        // 球正朝本方半场飞来（vx 指向网 = 指向我方）。旧符号写反，与 calculateTimeToNet
        // 内的 movingTowardsNet 互斥，timeToNet 恒为 0 → shouldJump 永远不通过，此入口从未生效
        boolean ballComingToMe = (computerX < netX && vx > 0) || (computerX > netX && vx < 0);
        if (!ballComingToMe) return;

        double timeToNet = calculateTimeToNet(netX, badmintonX, badmintonSpeedX, badmintonSpeedY);

        if (shouldJump(timeToNet)) {
            // 再做一次“可达性终检”（末刻取消）
            double idealHitY = computerY - GameProperties.hitAreaCenterHeight / 1.5;
            InterceptPrediction pred = predictAtY(idealHitY);
            if (pred.t > 0 && pred.t <= 1.0) {
                double reach = decisionMoveSpeed() * pred.t + hitAreaRadius + racketRadius + 8.0;
                if (Math.abs(pred.x - computerX) <= reach) {
                    isJump = true;
                }
            }
        }
    }

    private double calculateTimeToNet(double netX, double currentX, double vx, double vy) {
        double dt = GameProperties.frameTime, time = 0.0;
        double x = currentX, tvx = vx, tvy = vy;
        int steps = 0, maxSteps = 300;

        boolean movingTowardsNet = (computerX < netX && tvx > 0) || (computerX > netX && tvx < 0);
        if (!movingTowardsNet) return 0.0;

        while (Math.abs(x - netX) > 1 && steps < maxSteps) {
            double speed = Math.sqrt(tvx * tvx + tvy * tvy);
            double air = (speed == 0) ? 0 : 0.00001 * (speed * speed);
            if (speed == 0) {
                tvy += GameProperties.badmintonGravity * dt;
            } else {
                tvy += (GameProperties.badmintonGravity - 0.5 * air * (tvy / speed)) * dt;
                tvx -= 2.7 * air * (tvx / speed) * dt;
            }
            x += tvx * dt;
            time += dt;
            steps++;
        }
        return time;
    }

    private boolean shouldJump(double timeToNet) {
        boolean basicWindow = timeToNet > 0.22 && timeToNet < 0.9;
        if (!basicWindow) { rejMark("jWin"); return false; }
        if (isLikelyRecentWallBounce()) { rejMark("jWall"); return false; }
        // 高难度：起跳只留在本方前半场(离网190px内)——后场高球站定挑高远即可，
        // 起跳纯耗体力还容易变成"跳起来挑高球"的无效观赏；前场高球由前场跳扣分支处理
        if (smartEnergy && Math.abs(badmintonX - GameProperties.netPosition) > FRONT_COURT_WINDOW) { rejMark("jBack"); return false; }
        // 高难度：球低到“站立轻松够到”(肩上 45px 内)才不起跳——站立即打，起跳白费体力。
        // 原阈值整个触及圈105px、上一轮60px，60px 以上的偏高球仍常被排除导致跳跃偏少
        if (smartEnergy && badmintonY >= computerY - GameProperties.hitAreaRadius * 0.75) { rejMark("jLow"); return false; }
        // 体力管理：起跳要花体力，起跳后至少保留 15 余量确保还能出球，
        // 体力不足先不起跳，避免连跳把体力掏空后跑不动也打不出
        if (smartEnergy && energy < GameProperties.jumpEnergy + GameProperties.jumpEnergyReserve) { rejMark("jEne"); return false; }
        if (maxLevel) return true; // 满级恒起跳：跳过随机概率，双端一致

        // 档案门槛（数值即原实现：1级0.475/2级0.76/3级0.95/4级0.98/5级0.85）——
        // 4级更爱跳、5级跳跃收敛。roll 先取再比（调用次数不变，随机流不偏移）
        double threshold = profile.jumpGate;
        double roll = random.nextDouble();
        if (roll >= threshold) rejMark("jProb");
        return roll < threshold;
    }

    // 强化“刚碰墙反弹”检测：结合速度符号翻转 + 临近墙体 + 冷却帧
    private boolean isLikelyRecentWallBounce() {
        if (wallBounceCooldownFrames > 0) return true;

        double leftGap  = badmintonX - GameProperties.playFieldLeft;
        double rightGap = GameProperties.playFieldRight - badmintonX;

        boolean nearLeftAndInward  = leftGap  >= 0 && leftGap  < WALL_BOUNCE_GAP && badmintonSpeedX > 0;
        boolean nearRightAndInward = rightGap >= 0 && rightGap < WALL_BOUNCE_GAP && badmintonSpeedX < 0;

        return nearLeftAndInward || nearRightAndInward;
    }

    // 跨帧维护墙反弹冷却
    private void updateWallBounceCooldown() {
        if (!Double.isNaN(prevBallVX)) {
            boolean signFlip = (prevBallVX >  80 && badmintonSpeedX < -80) ||
                    (prevBallVX < -80 && badmintonSpeedX >  80);
            boolean nearWall = Math.min(Math.abs(badmintonX - GameProperties.playFieldLeft),
                    Math.abs(GameProperties.playFieldRight - badmintonX)) < WALL_BOUNCE_GAP;
            if (signFlip && nearWall) {
                wallBounceCooldownFrames = Math.max(wallBounceCooldownFrames, WALL_BOUNCE_COOLDOWN_DEFAULT);
            }
        }
        if (wallBounceCooldownFrames > 0) {
            wallBounceCooldownFrames--; // 每帧递减
        }
    }

    // 预测球在目标高度 targetY 时的 X 与所需时间。
    // 与 preLanding 同款口径（当年 preLanding 的三处修复此处曾漏）：
    // ①球已在目标高度或更低且不再上升 → 永远穿不过目标，早退返回当前X——
    //   否则空转 maxSteps 拿到 5 秒假预测，调用方(防守对位)会追假点乱跑(P1 失分根因)；
    // ②Δv 按步施加不乘 frameTime（乘 dt 加速度弱60倍，与真实轨迹脱节）；
    // ③触墙 0.6 反弹，避免预测点飞出墙外
    private InterceptPrediction predictAtY(double targetY) {
        double dt = GameProperties.frameTime;

        double x = badmintonX;
        double y = badmintonY;
        double vx = badmintonSpeedX;
        double vy = badmintonSpeedY;

        // 不可达早退：球已在目标高度或下方且仍下落 → 永远穿不过上方的目标
        // （与 preLanding 同款；否则空转 maxSteps 拿到 5 秒假预测，调用方追假点乱跑）
        if (y >= targetY && vy >= 0) return new InterceptPrediction(x, 0.0, vy);

        double time = 0.0;
        int steps = 0, maxSteps = 300;
        double prevY = y;

        while (steps < maxSteps) {
            double speed = Math.sqrt(vx * vx + vy * vy);

            if (speed == 0) {
                vy += GameProperties.badmintonGravity;
            } else {
                double air = 0.00001 * (speed * speed);
                vx -= 2.7 * air * (vx / speed);
                vy += GameProperties.badmintonGravity - 0.5 * air * (vy / speed);
            }

            // 触墙反弹：与 Badminton.onUpdate 的钳位+0.6恢复系数一致
            double nextX = x + vx * dt;
            if (nextX <= GameProperties.playFieldLeft || nextX >= GameProperties.playFieldRight) {
                double leftSq = (x - GameProperties.playFieldLeft) * (x - GameProperties.playFieldLeft);
                double rightSq = (GameProperties.playFieldRight - x) * (GameProperties.playFieldRight - x);
                x = leftSq < rightSq ? GameProperties.playFieldLeft : GameProperties.playFieldRight;
                vx = -vx * 0.6;
            }

            x += vx * dt;
            y += vy * dt;
            time += dt;

            if ((prevY - targetY) * (y - targetY) <= 0) break;

            prevY = y;
            steps++;
        }

        return new InterceptPrediction(x, time, vy);
    }

    private static class InterceptPrediction {
        final double x;
        final double t;
        final double vy;
        InterceptPrediction(double x, double t, double vy) { this.x = x; this.t = t; this.vy = vy; }
    }

    // 扣杀重击：恒带起跳（扣杀必须先跳再击球，站立重扣路径已删除）。
    // 体力管理：跳杀 = 跳20 + 重击35 = 55，体力不足则降级为普通击球；
    // 否则出招会被 StickMan 按“体力>35”规则静默降级成轻击，白跳一次还亏体力
    public void superShot() {
        double cost = GameProperties.jumpEnergy + GameProperties.heavyShotEnergy;
        if (smartEnergy && energy <= cost) {
            middleShot();
            return;
        }
        isHeavyhit = true;
        isLighthit = false;
        isJump = true;
        isSmashIntent = true; // 扣杀意图：接触层据此做下压角爆发处理

        boolean isRightSide = computerX > GameProperties.netPosition;
        double opponentDistance = Math.abs(opponentX - computerX);
        double targetAngle = isRightSide
                ? ((opponentDistance > 300) ? 165 : 155)
                : ((opponentDistance > 300) ? 15  : 25);
        targetAngle += (new Random().nextDouble() - 0.5) * 10;
        superShotAngleDeg = targetAngle;
    }

    // 轻击高球
    public void highShot() { isLighthit = true; isHeavyhit = false; }

    // 中场球
    public void middleShot() {
        double distance = Math.abs(computerX - GameProperties.netPosition); // 自己离网距离，左右半场对称
        // 体力管理：体力已不够一次重击就直接轻击（重击门槛是体力>35，边缘值会被静默降级，
        // 主动降级可让意图与实际动作一致）；满级路径按体力判断也是确定性的，双端不分叉
        boolean energyLight = smartEnergy && energy <= GameProperties.heavyShotEnergy;
        // 满级恒重击：30% 轻击随机会令双端 energyRemain 分叉 → moveSpeed 分叉
        if (energyLight || (!maxLevel && new Random().nextDouble() < 0.3 && distance > 200)) {
            isLighthit = true; isHeavyhit = false;
        } else {
            isHeavyhit = true; isLighthit = false;
        }
    }

    public double getAngle() {
        // 使用标准 atan2(vy, vx)
        if (Math.abs(badmintonSpeedX) < 1e-6 && Math.abs(badmintonSpeedY) < 1e-6) return 0;
        double radians = Math.atan2(badmintonSpeedY, badmintonSpeedX);
        double degrees = Math.toDegrees(radians);
        if (degrees < 0) degrees += 360;
        return degrees;
    }

    private double getBallSpeed() {
        return Math.sqrt(badmintonSpeedX * badmintonSpeedX + badmintonSpeedY * badmintonSpeedY);
    }

    private double getDistance() {
        double dx = Math.abs(computerX - badmintonX);
        double dy = Math.abs(computerY - badmintonY);
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double map(double v, double inMin, double inMax, double outMin, double outMax) {
        if (inMax == inMin) return (outMin + outMax) * 0.5;
        double t = (v - inMin) / (inMax - inMin);
        t = Math.max(0.0, Math.min(1.0, t));
        return outMin + (outMax - outMin) * t;
    }
}