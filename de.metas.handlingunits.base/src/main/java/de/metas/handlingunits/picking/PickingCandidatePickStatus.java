package de.metas.handlingunits.picking;

import java.util.stream.Stream;

import org.adempiere.exceptions.AdempiereException;

import com.google.common.collect.ImmutableMap;

import de.metas.handlingunits.model.X_M_Picking_Candidate;
import de.metas.util.GuavaCollectors;
import lombok.Getter;
import lombok.NonNull;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2018 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public enum PickingCandidatePickStatus
{
	TO_BE_PICKED(X_M_Picking_Candidate.PICKSTATUS_ToBePicked), //
	PICKED(X_M_Picking_Candidate.PICKSTATUS_Picked), //
	WILL_NOT_BE_PICKED(X_M_Picking_Candidate.PICKSTATUS_WillNotBePicked) //
	;
	private static ImmutableMap<String, PickingCandidatePickStatus> typesByCode = Stream.of(values())
			.collect(GuavaCollectors.toImmutableMapByKey(PickingCandidatePickStatus::getCode));

	@Getter
	private String code;

	private PickingCandidatePickStatus(final String code)
	{
		this.code = code;
	}

	public static PickingCandidatePickStatus ofCode(@NonNull final String code)
	{
		final PickingCandidatePickStatus type = typesByCode.get(code);
		if (type == null)
		{
			throw new AdempiereException("No " + PickingCandidatePickStatus.class + " found for: " + code);
		}
		return type;
	}

	public boolean isToBePicked()
	{
		return TO_BE_PICKED.equals(this);
	}
}