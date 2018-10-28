package de.metas.ordercandidate.rest;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/*
 * #%L
 * de.metas.ordercandidate.rest-api
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

@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
@Value
public class JsonOLCandCreateBulkRequest
{
	public static JsonOLCandCreateBulkRequest of(@NonNull final JsonOLCandCreateRequest request)
	{
		return builder().request(request).build();
	}

	List<JsonOLCandCreateRequest> requests;

	@JsonCreator
	@Builder
	public JsonOLCandCreateBulkRequest(
			@JsonProperty("requests") @Singular final List<JsonOLCandCreateRequest> requests)
	{
		this.requests = requests;
	}

	public JsonOLCandCreateBulkRequest validate()
	{
		for (final JsonOLCandCreateRequest request : requests)
		{
			request.validate();
		}
		return this;
	}
}
