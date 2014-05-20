/*
 * This program was produced for the U.S. Agency for International Development. It was prepared by the USAID | DELIVER PROJECT, Task Order 4. It is part of a project which utilizes code originally licensed under the terms of the Mozilla Public License (MPL) v2 and therefore is licensed under MPL v2 or later.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the Mozilla Public License as published by the Mozilla Foundation, either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the Mozilla Public License for more details.
 *
 * You should have received a copy of the Mozilla Public License along with this program. If not, see http://www.mozilla.org/MPL/
 */

package org.openlmis.equipment.repository.mapper;

import org.apache.ibatis.annotations.*;
import org.openlmis.equipment.domain.MaintenanceRequest;
import org.openlmis.equipment.domain.ServiceContract;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRequestMapper {

  @Select("select * from equipment_maintenance_requests where id = #{id}")
  MaintenanceRequest getById(Long id);

  @Select("select * from equipment_maintenance_requests")
  List<MaintenanceRequest> getAll();

  @Select("select * from equipment_maintenance_requests where facilityId = #{facilityId}")
  List<MaintenanceRequest> getAllForFacility(@Param("facilityId") Long facilityId);

  @Select("select * from equipment_maintenance_requests where vendorId = #{vendorId}")
  List<MaintenanceRequest> getAllForVendor(@Param("vendorId") Long vendorId);

  @Select("select * from equipment_maintenance_requests where vendorId = #{vendorId} and resolved = false")
  List<MaintenanceRequest> getOutstandingRequestsForVendor(@Param("vendorId") Long vendorId);

  @Insert("insert into equipment_maintenance_requests (userId, facilityId, inventoryId, vendorId, requestDate, reason, recommendedDate, comment, resolved, vendorComment, createdBy, createdDate, modifiedBy, modifiedDate) " +
      " values " +
      " (#{userId}, #{facilityId}, #{inventoryId}, #{vendorId}, #{requestDate}, #{reason}, #{recommendedDate}, #{comment}, #{resolved}, #{vendorComment} , #{createdBy},COALESCE(#{createdDate}, NOW()), #{modifiedBy}, NOW() )")
  @Options(useGeneratedKeys = true)
  void insert(MaintenanceRequest value);

  @Update("UPDATE equipment_maintenance_requests SET " +
      "userId = #{userId}, facilityId = #{facilityId}, inventoryId = #{inventoryId}, vendorId = #{vendorId}, requestDate = #{requestDate}, reason = #{reason}, recommendedDate = #{recommendedDate}, comment = #{comment}, resolved = #{resolved}, vendorComment = #{vendorComment}, modifiedBy = #{modifiedBy}, modifiedDate = NOW()" +
      " WHERE id = #{id}")
  void update(MaintenanceRequest value);
}
