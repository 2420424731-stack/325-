package com.family.finance.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.family.finance.common.BizException;
import com.family.finance.dto.LoginDTO;
import com.family.finance.dto.RegisterDTO;
import com.family.finance.entity.Category;
import com.family.finance.entity.Family;
import com.family.finance.entity.FamilyMember;
import com.family.finance.entity.User;
import com.family.finance.mapper.CategoryMapper;
import com.family.finance.mapper.FamilyMapper;
import com.family.finance.mapper.FamilyMemberMapper;
import com.family.finance.mapper.UserMapper;
import com.family.finance.service.AuthService;
import com.family.finance.vo.LoginVO;
import com.family.finance.vo.UserContextVO;
import com.family.finance.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证实现：注册建号建家、登录签发、上下文查询
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 收入分类（一级） */
    private static final List<String> INCOME_TOP = List.of("工资奖金", "经营收入", "投资收益", "礼金红包", "其他收入");
    /** 支出分类（一级 → 二级，LinkedHashMap 保证顺序） */
    private static final Map<String, List<String>> EXPENSE_TREE = new LinkedHashMap<>() {{
        put("餐饮支出", List.of("在家做饭", "外卖", "外出就餐"));
        put("购物支出", List.of("服饰类", "日用品类", "电子产品类", "其他购物"));
        put("交通支出", List.of("公共交通", "打车", "车辆用度"));
        put("住房支出", List.of("房租", "物业水电", "装修维护"));
        put("医疗教育", List.of("医疗", "教育", "保险"));
        put("人情往来", List.of("礼尚往来", "孝敬父母"));
        put("休闲娱乐", List.of());
        put("其他支出", List.of());
    }};

    private final UserMapper userMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final CategoryMapper categoryMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        // 1. 用户名唯一校验
        Long exist = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exist > 0) {
            throw new BizException("用户名已存在");
        }

        // 2. 创建账号（管理员 = 户主）
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() == null ? dto.getUsername() : dto.getNickname());
        user.setRole("ADMIN");
        user.setStatus(1);
        userMapper.insert(user);

        // 3. 创建家庭
        Family family = new Family();
        family.setName(dto.getFamilyName());
        family.setCreatedBy(user.getId());
        familyMapper.insert(family);

        // 4. 创建户主成员
        FamilyMember owner = new FamilyMember();
        owner.setFamilyId(family.getId());
        owner.setUserId(user.getId());
        owner.setName(user.getNickname());
        owner.setRelation("户主");
        owner.setSortOrder(0);
        owner.setStatus(1);
        familyMemberMapper.insert(owner);

        // 5. 初始化内置收支分类（课程要求③：分类可自定义，内置为基础）
        initDefaultCategories(family.getId());

        return doLogin(user.getId());
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(400, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }
        return doLogin(user.getId());
    }

    @Override
    public UserContextVO me() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("账号不存在");
        }
        // 用户默认绑定第一个家庭（注册即建家；家庭管理功能后续迭代）
        FamilyMember member = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>().eq(FamilyMember::getUserId, userId)
                        .orderByAsc(FamilyMember::getId).last("LIMIT 1"));
        UserContextVO vo = new UserContextVO();
        vo.setUser(UserVO.from(user));
        if (member != null) {
            Family family = familyMapper.selectById(member.getFamilyId());
            vo.setFamily(family);
            vo.setMembers(familyMemberMapper.selectList(
                    new LambdaQueryWrapper<FamilyMember>()
                            .eq(FamilyMember::getFamilyId, member.getFamilyId())
                            .eq(FamilyMember::getStatus, 1)
                            .orderByAsc(FamilyMember::getSortOrder)));
        }
        return vo;
    }

    /** 签发令牌并组装登录响应 */
    private LoginVO doLogin(Long userId) {
        StpUtil.login(userId);
        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUser(UserVO.from(userMapper.selectById(userId)));
        return vo;
    }

    /** 初始化内置收支分类：先一级后二级（全部 is_system=1，前端禁删） */
    private void initDefaultCategories(Long familyId) {
        int typeIncome = 1;
        int typeExpense = 2;

        // 收入一级
        Map<String, Long> incomeTopIds = new LinkedHashMap<>();
        for (String name : INCOME_TOP) {
            incomeTopIds.put(name, insertCategory(familyId, 0L, typeIncome, name, 0, 1));
        }
        // 收入二级暂不需要，后续可扩展

        // 支出一级 + 二级
        int sort = 0;
        for (Map.Entry<String, List<String>> entry : EXPENSE_TREE.entrySet()) {
            long parentId = insertCategory(familyId, 0L, typeExpense, entry.getKey(), sort++, 1);
            int childSort = 0;
            for (String child : entry.getValue()) {
                insertCategory(familyId, parentId, typeExpense, child, childSort++, 0);
            }
        }
    }

    /** 插入一条系统内置分类，返回其 id */
    private long insertCategory(Long familyId, Long parentId, int type, String name, int sortOrder, int isSystem) {
        Category c = new Category();
        c.setFamilyId(familyId);
        c.setParentId(parentId);
        c.setType(type);
        c.setName(name);
        c.setSortOrder(sortOrder);
        c.setIsSystem(isSystem);
        c.setStatus(1);
        categoryMapper.insert(c);
        return c.getId();
    }
}
