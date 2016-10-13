//
//  NSMutableURLRequestExtensions.swift
//  Mobile
//
//  Created by Jason Hocker on 5/13/16.
//  Copyright © 2016 Ellucian Company L.P. and its affiliates. All rights reserved.
//

import Foundation

extension NSMutableURLRequest {
    
    func addAuthenticationHeader() {
        if (LoginExecutor.isUsingBasicAuthentication()) {
            let user = CurrentUser.sharedInstance
            if let userauth = user.userauth, let password = user.getPassword() {
                let loginString = "\(userauth):\(password)"
                
                let plainData = loginString.data(using: String.Encoding.utf8)
                if let encodedLoginData = plainData?.base64EncodedString() {
                    let authHeader = "Basic ".appendingFormat("%@", encodedLoginData)
                    self.addValue(authHeader, forHTTPHeaderField: "Authorization")
                }
            }
        }
    }

}
