/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p>
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.afyastat.api.db.hibernate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.afyastat.api.db.AfyastatDao;
import org.openmrs.module.afyastat.api.service.MedicQueData;

public class HibernateAfyaStatDAO implements AfyastatDao {
	
	protected final Log log = LogFactory.getLog(this.getClass());
	
	private DbSessionFactory sessionFactory;
	
	/**
	 * @Autowired private HTSDAO htsDAO;
	 */
	
	/**
	 * @param sessionFactory the sessionFactory to set
	 */
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	public DbSessionFactory getSessionFactory() {
		return sessionFactory;
	}
	
	/**
	 * @return the sessionFactory
	 */
	
	@Override
	public MedicQueData saveQueData(MedicQueData medicQueData) throws DAOException {
		
		sessionFactory.getCurrentSession().saveOrUpdate(medicQueData);
		return medicQueData;
	}
}
