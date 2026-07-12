package cn.nihility.rbac.org.mapstruct;

import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgTreeNodeVO;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.entity.OrgEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-12T16:04:39+0800",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.1 (Oracle Corporation)"
)
public class OrgConvertImpl implements OrgConvert {

    @Override
    public OrgVO toVO(OrgEntity entity) {
        if ( entity == null ) {
            return null;
        }

        OrgVO.OrgVOBuilder orgVO = OrgVO.builder();

        orgVO.id( entity.getId() );
        orgVO.name( entity.getName() );
        orgVO.code( entity.getCode() );
        orgVO.parentId( entity.getParentId() );
        orgVO.status( entity.getStatus() );
        orgVO.showOrder( entity.getShowOrder() );
        orgVO.createBy( entity.getCreateBy() );
        orgVO.createTime( entity.getCreateTime() );
        orgVO.updateBy( entity.getUpdateBy() );
        orgVO.updateTime( entity.getUpdateTime() );

        return orgVO.build();
    }

    @Override
    public List<OrgVO> toVOList(List<OrgEntity> entities) {
        if ( entities == null ) {
            return null;
        }

        List<OrgVO> list = new ArrayList<OrgVO>( entities.size() );
        for ( OrgEntity orgEntity : entities ) {
            list.add( toVO( orgEntity ) );
        }

        return list;
    }

    @Override
    public OrgTreeNodeVO toTreeNode(OrgEntity entity) {
        if ( entity == null ) {
            return null;
        }

        OrgTreeNodeVO.OrgTreeNodeVOBuilder orgTreeNodeVO = OrgTreeNodeVO.builder();

        orgTreeNodeVO.id( entity.getId() );
        orgTreeNodeVO.name( entity.getName() );
        orgTreeNodeVO.code( entity.getCode() );
        orgTreeNodeVO.parentId( entity.getParentId() );
        orgTreeNodeVO.status( entity.getStatus() );
        orgTreeNodeVO.showOrder( entity.getShowOrder() );

        return orgTreeNodeVO.build();
    }

    @Override
    public OrgEntity toEntity(OrgCreateRequest request) {
        if ( request == null ) {
            return null;
        }

        OrgEntity.OrgEntityBuilder orgEntity = OrgEntity.builder();

        orgEntity.name( request.getName() );
        orgEntity.code( request.getCode() );
        orgEntity.parentId( request.getParentId() );
        orgEntity.showOrder( request.getShowOrder() );

        return orgEntity.build();
    }

    @Override
    public void updateEntity(OrgUpdateRequest request, OrgEntity entity) {
        if ( request == null ) {
            return;
        }

        entity.setName( request.getName() );
        entity.setCode( request.getCode() );
        entity.setParentId( request.getParentId() );
        entity.setShowOrder( request.getShowOrder() );
    }
}
